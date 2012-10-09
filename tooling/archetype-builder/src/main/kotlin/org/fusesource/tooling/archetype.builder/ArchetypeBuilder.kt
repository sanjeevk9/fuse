package org.fusesource.tooling.archetype.builder

import java.io.File
import kotlin.dom.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.FileWriter

val sourceFileExtensions = hashSet(
        "bpmn",
        "drl",
        "html",
        "groovy",
        "jade",
        "java",
        "jbpm",
        "js",
        "json",
        "jsp",
        "kotlin",
        "ks",
        "md",
        "properties",
        "scala",
        "ssp",
        "ts",
        "txt",
        "xml"
)

val excludeExtensions = hashSet("iml", "iws", "ipr")

val sourceCodeDirNames = arrayList("java", "kotlin", "scala")

val sourceCodeDirPaths = (
sourceCodeDirNames.map { "src/main/$it" } +
sourceCodeDirNames.map { "src/test/$it" } +
arrayList("target", "build", "pom.xml")).toSet()

public open class ArchetypeBuilder() {
    public open fun configure(args: Array<String>): Unit {
    }

    public open fun generateArchetypes(sourceDir: File, outputDir: File): Unit {
        println("Generating archetypes from sourceDir: $sourceDir")
        val files = sourceDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory()) {
                    var pom = File(file, "pom.xml")
                    if (pom.exists()) {
                        val outputName = file.name.replace("example", "archetype")
                        generateArchetype(file, pom, File(outputDir, outputName))
                    }
                }
            }
        }
    }

    protected open fun generateArchetype(directory: File, pom: File, outputDir: File): Unit {
        println("Generating archetype at $outputDir from $directory")
        val srcDir = File(directory, "src/main")
        val testDir = File(directory, "src/test")
        var archetypeOutputDir = File(outputDir, "src/main/resources/archetype-resources")


        var replaceFunction = {(s: String) -> s }
        val mainSrcDir = sourceCodeDirNames.map{ File(srcDir, it) }.find { it.exists() }
        if (mainSrcDir != null) {
            // lets find the first directory which contains more than one child
            // to find the root-most package
            val rootPackage = findRootPackage(mainSrcDir)

            if (rootPackage != null) {
                val packagePath = mainSrcDir.relativePath(rootPackage)
                val packageName = packagePath.replaceAll("/", ".") // .replaceAll("/", "\\\\.")
                val regex = packageName.replaceAll("\\.", "\\\\.")

                replaceFunction = {(s: String) -> s.replaceAll(regex, "\\\${package}") }

                // lets recursively copy files replacing the package names
                val outputMainSrc = File(archetypeOutputDir, directory.relativePath(mainSrcDir))
                copyCodeFiles(rootPackage, outputMainSrc, replaceFunction)

                val testSrcDir = sourceCodeDirNames.map{ File(testDir, it) }.find { it.exists() }
                if (testSrcDir != null) {
                    val rootTestDir = File(testSrcDir, packagePath)
                    val outputTestSrc = File(archetypeOutputDir, directory.relativePath(testSrcDir))
                    if (rootTestDir.exists()) {
                        copyCodeFiles(rootTestDir, outputTestSrc, replaceFunction)
                    } else {
                        copyCodeFiles(testSrcDir, outputTestSrc, replaceFunction)
                    }
                }
            }
        }
        copyPom(pom, File(archetypeOutputDir, "pom.xml"), replaceFunction)

        // now lets copy all non-ignored files across
        copyOtherFiles(directory, directory, archetypeOutputDir, replaceFunction)
    }

    /**
     * Copies all java/kotlin/scala code
     */
    protected open fun copyCodeFiles(srcDir: File, outDir: File, replaceFn: (String) -> String): Unit {
        if (srcDir.isFile()) {
            copyFile(srcDir, outDir, replaceFn)
        } else {
            outDir.mkdirs()
            val names = srcDir.list()
            if (names != null) {
                for (name in names) {
                    copyCodeFiles(File(srcDir, name), File(outDir, name), replaceFn)
                }
            }
        }
    }

    protected fun copyPom(pom: File, outFile: File, replaceFn: (String) -> String): Unit {
        val text = replaceFn(pom.readText())

        // lets update the XML
        val doc = parseXml(InputSource(StringReader(text)))
        val root = doc.documentElement
        if (root != null) {
            // remove the parent element
            val parents = root.elements("parent")
            if (parents.notEmpty()) {
                root.removeChild(parents[0])
            }

            // now lets replace the contents of some elements (adding new elements if they are not present)
            val beforeNames = arrayList("artifactId", "version", "packaging", "name", "properties")
            replaceOrAddElement(doc, root, "version", "\${version}", beforeNames)
            replaceOrAddElement(doc, root, "artifactId", "\${artifactId}", beforeNames)
            replaceOrAddElement(doc, root, "groupId", "\${groupId}", beforeNames)
        }
        doc.writeXmlString(FileWriter(outFile), true)
    }

    protected fun replaceOrAddElement(doc: Document, parent: Element, name: String, content: String, beforeNames: Iterable<String>) {
        val elements = parent.children().filter { it.nodeName == name }
        val element = if (elements.isEmpty()) {
            val newElement = doc.createElement(name)
            val before = beforeNames.map{ parent.elements(it).first }
            val node = before.first ?: parent.getFirstChild()
            println("Inserting new element with name $name before element ${node?.toXmlString()}")
            val text = doc.createTextNode("\n  ")
            parent.insertBefore(text, node)
            parent.insertBefore(newElement, text)
            newElement
        } else {
            elements[0]
        }
        element!!.text = content
    }

    protected fun copyFile(src: File, dest: File, replaceFn: (String) -> String): Unit {
        if (isSourceFile(src)) {
            val text = replaceFn(src.readText())
            dest.writeText(text)
        } else {
            println("Not a source dir as the extention is ${src.extension}")
            src.copyTo(dest)
        }
    }

    /**
     * Copies all other source files which are not excluded
     */
    protected open fun copyOtherFiles(projectDir: File, srcDir: File, outDir: File, replaceFn: (String) -> String): Unit {
        if (isValidFileToCopy(projectDir, srcDir)) {
            if (srcDir.isFile()) {
                copyFile(srcDir, outDir, replaceFn)
            } else {
                outDir.mkdirs()
                val names = srcDir.list()
                if (names != null) {
                    for (name in names) {
                        copyOtherFiles(projectDir, File(srcDir, name), File(outDir, name), replaceFn)
                    }
                }
            }
        }
    }


    protected open fun findRootPackage(directory: File): File? {
        val children = directory.listFiles { isValidSourceFileOrDir(it) }
        if (children != null) {
            val results = children.map { findRootPackage(it) }.filter { it != null }
            if (results.size == 1) {
                return results[0]
            } else {
                return directory
            }
        }
        return null
    }

    /**
     * Returns true if this file is a valid source file; so
     * excluding things like .svn directories and whatnot
     */
    protected open fun isValidSourceFileOrDir(file: File): Boolean {
        val name = file.name
        return !name.startsWith(".") && !excludeExtensions.contains(file.extension)
    }

    /**
     * Returns true if this file is a valid source file name
     */
    protected open fun isSourceFile(file: File): Boolean {
        val name = file.extension.toLowerCase()
        return sourceFileExtensions.contains(name)
    }

    /**
     * Is the file a valid file to copy (excludes files starting with a dot, build output
     * or java/kotlin/scala source code
     */
    protected open fun isValidFileToCopy(projectDir: File, src: File): Boolean {
        if (isValidSourceFileOrDir(src)) {
            if (src == projectDir) return true
            val relative = projectDir.relativePath(src)
            return relative != "target" && relative != "build" && !sourceCodeDirPaths.contains(relative)
        }
        return false
    }
}