/*
 * sbt IO
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.io

import java.io.File
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import sbt.io.syntax._

class IOSpec extends AnyFunSuite {

  test("IO should relativize") {
    // Given:
    // io-relativize/
    //     meh.file
    //     inside-dir/
    //
    // and
    // relativeRootDir referring to io-relativize/inside-dir/../

    val rootDir = Files.createTempDirectory("io-relativize").normalize
    val nestedFile = Files.createFile(rootDir resolve "meh.file").toFile
    val nestedDir = Files.createDirectory(rootDir resolve "inside-dir").toFile

    val relativeRootDir = new File(nestedDir, "..")
    assert(
      IO.relativize(rootDir.toFile, nestedFile)
        .map(normalizeForWindows) === Option("meh.file")
    )
    assert(
      IO.relativize(relativeRootDir, nestedFile).map(normalizeForWindows) === Option("meh.file")
    )
    IO.delete(rootDir.toFile)
  }

  test("it should relativize . dirs") {
    val base = new File(".")
    val file1 = new File("./.git")
    val file2 = new File(".", ".git")
    val file3 = new File(base, ".git")

    assert(IO.relativize(base, file1) == Some(".git"))
    assert(IO.relativize(base, file2) == Some(".git"))
    assert(IO.relativize(base, file3) == Some(".git"))
  }

  test("it should relativize relative paths") {
    val base = new File(".").getCanonicalFile
    val file = new File("build.sbt")
    assert(IO.relativize(base, file) == Some("build.sbt"))
  }

  test("it should copy directories") {
    IO.withTemporaryDirectory { dir =>
      val subdir1 = new File(dir, "subdir1")
      val nestedSubdir1 = new File(subdir1, "nested")
      val subdir1File = new File(nestedSubdir1, "file")
      IO.createDirectory(nestedSubdir1)
      IO.write(subdir1File, "foo-1")
      val subdir2 = new File(dir, "subdir2")
      val nestedSubdir2 = new File(subdir2, "nested")
      val subdir2File = new File(nestedSubdir2, "file")
      IO.createDirectory(nestedSubdir2)
      IO.write(subdir2File, "foo-2")
      val copied = new File(dir, "copy")
      val copiedNested = new File(copied, "nested")
      val copiedFile = new File(copiedNested, "file")
      IO.copyDirectory(subdir1, copied)
      assert(PathFinder(copied).allPaths.get().toSet == Set(copied, copiedNested, copiedFile))
      assert(IO.read(copiedFile) == "foo-1")
      IO.delete(copied)
      IO.copyDirectory(subdir2, copied)
      assert(PathFinder(copied).allPaths.get().toSet == Set(copied, copiedNested, copiedFile))
      assert(IO.read(copiedFile) == "foo-2")
    }
  }

  test("toURI should make URI") {
    val u = IO.toURI(file("/etc/hosts"))
    assert(u.toString == "file:///etc/hosts")
  }

  test("it should make u0 URI from a relative path") {
    val u = IO.toURI(file("src") / "main" / "scala")
    assert(u.toString == "src/main/scala")
  }

  test("it should make u0 URI from a relative path on Windows") {
    if (IO.isWindows) {
      val input = file("""..\My Documents\test""")
      val u = IO.toURI(input)
      assert(u.toString == "../My%20Documents/test" && IO.toFile(u) == input)
    } else ()
  }

  test("it should make URI that roundtrips") {
    val u = IO.toURI(file("/etc/hosts"))
    assert(IO.toFile(u) == file("/etc/hosts"))
  }

  test("it should make u0 URI that roundtrips") {
    val u = IO.toURI(file("src") / "main" / "scala")
    assert(IO.toFile(u) == (file("src") / "main" / "scala"))
  }

  test("it should make u3 URI for an absolute path on Windows that roundtrips") {
    if (IO.isWindows) {
      val input = file("""C:\Documents and Settings\""")
      val u = IO.toURI(input)
      assert(u.toString == "file:///C:/Documents%20and%20Settings" && IO.toFile(u) == input)
    } else ()
  }

  test("it should make u2 URI for a UNC path on Windows that roundtrips") {
    if (IO.isWindows) {
      val input = file("""\\laptop\My Documents\Some.doc""")
      val u = IO.toURI(input)
      assert(u.toString == "file://laptop/My%20Documents/Some.doc" && IO.toFile(u) == input)
    } else ()
  }

  test("getModifiedTimeOrZero should return 0L if the file doesn't exists") {
    assert(IO.getModifiedTimeOrZero(file("/not/existing/path")) == 0L)
  }

  // classLocation -----------

  test("classLocation[Integer] should return a URL pointing to the rt.jar or java.base") {
    val u = IO.classLocation[java.lang.Integer]
    assert(
      (u.toString == "jrt:/java.base") ||
        (u.toString.startsWith("file:") && u.toString.endsWith("rt.jar") && IO.asFile(u).isFile)
    )
  }

  test(
    "classLocation[AbstractMap.SimpleEntry] should return a URL pointing to the rt.jar or java.base"
  ) {
    val u = IO.classLocation[java.util.AbstractMap.SimpleEntry[String, String]]
    assert(
      (u.toString == "jrt:/java.base") ||
        (u.toString.startsWith("file:") && u.toString.endsWith("rt.jar") && IO.asFile(u).isFile)
    )
  }

  test("classLocation[this.type] should return a URL pointing to a directory or a JAR") {
    val u = IO.classLocation[this.type]
    assert(IO.asFile(u).exists)
  }

  // classLocationPath -----------

  test("classLocationPath[Integer] should return NIO path pointing to the rt.jar or java.base") {
    val p = IO.classLocationPath[java.lang.Integer]
    assert(
      ((p.toString == "/java.base") && (p.getFileSystem.toString == "jrt:/"))
        || (p.toString.endsWith("rt.jar") && p.toFile.isFile)
    )
  }

  test(
    "classLocationPath[AbstractMap.SimpleEntry] should return NIO path pointing to the rt.jar or java.base"
  ) {
    val p = IO.classLocationPath[java.util.AbstractMap.SimpleEntry[String, String]]
    assert(
      ((p.toString == "/java.base") && (p.getFileSystem.toString == "jrt:/"))
        || (p.toString.endsWith("rt.jar") && p.toFile.isFile)
    )
  }

  test("classLocationPath[this.type] should return NIO path pointing to a directory or a JAR") {
    val p = IO.classLocationPath[this.type]
    assert(p.toFile.exists)
  }

  // classLocationFileOption -----------

  test("classLocationFileOption[Integer] should return File pointing to the rt.jar or None") {
    val opt = IO.classLocationFileOption[java.lang.Integer]
    assert(
      opt match {
        case Some(x) => x.getName == "rt.jar"
        case None    => true
      }
    )
  }

  test(
    "classLocationFileOption[AbstractMap.SimpleEntry] should return File pointing to the rt.jar or None"
  ) {
    val opt = IO.classLocationFileOption[java.util.AbstractMap.SimpleEntry[String, String]]
    assert(
      opt match {
        case Some(x) => x.getName == "rt.jar"
        case None    => true
      }
    )
  }

  test("classLocationFileOption[this.type] should return File pointing to a directory or a JAR") {
    val opt = IO.classLocationFileOption[this.type]
    assert(opt.get.exists)
  }

  // classfileLocation -----------

  test("classfileLocation[Integer] should return a URL pointing to *.class") {
    val s = IO.classfileLocation[java.lang.Integer].toString
    assert(
      (s == "jrt:/java.base/java/lang/Integer.class") ||
        (s.startsWith("jar:file:") && s.endsWith("!/java/lang/Integer.class"))
    )
  }

  test("classfileLocation[AbstractMap.SimpleEntry] should return a URL pointing to *.class") {
    val s = IO.classfileLocation[java.util.AbstractMap.SimpleEntry[String, String]].toString
    assert(
      (s == "jrt:/java.base/java/util/AbstractMap$SimpleEntry.class") ||
        (s.startsWith("jar:file:") && s.endsWith("!/java/util/AbstractMap$SimpleEntry.class"))
    )
  }

  test("classfileLocation[this.type] should return a URL pointing to *.class") {
    val s = IO.classfileLocation[this.type].toString
    assert(s.startsWith("file:") && s.endsWith("/sbt/io/IOSpec.class"))
  }

  test("delete should handle non-existent files") {
    IO.withTemporaryDirectory { dir =>
      IO.delete(new File(dir, "foo"))
    }
  }

  test("move should overwrite") {
    IO.withTemporaryDirectory { dir =>
      IO.touch(dir / "a.txt")
      IO.touch(dir / "b.txt")
      IO.move(dir / "a.txt", dir / "b.txt")
    }
  }

  test("it should create valid jar files") {
    IO.withTemporaryDirectory { tmpdir =>
      import java.util.jar.Manifest
      import java.nio.file.FileSystems
      import java.nio.file.Paths
      import java.nio.charset.StandardCharsets
      import java.util.Collections

      val fooTxt = tmpdir / "foo.txt"
      Files.write(fooTxt.toPath, "payload".getBytes(StandardCharsets.UTF_8));

      val testJar = tmpdir / "test.jar"
      IO.jar(List((fooTxt, "foo.txt")), testJar, new Manifest(), Some(1577195222))

      val fooTxtInJar = new URI(s"jar:${testJar.toURI}!/foo.txt")

      val fs = FileSystems.newFileSystem(fooTxtInJar, Collections.emptyMap[String, Object])
      try {
        assert("payload" == new String(Files.readAllBytes(Paths.get(fooTxtInJar))))
        // Read to check it exists:
        Files.readAllBytes(Paths.get(new URI(s"jar:${testJar.toURI}!/META-INF/MANIFEST.MF")))
      } finally fs.close()
    }
  }

  def normalizeForWindows(s: String): String = {
    s.replaceAllLiterally("""\""", "/")
  }
}
