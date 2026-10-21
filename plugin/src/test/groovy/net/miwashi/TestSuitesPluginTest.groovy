package net.miwashi

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.File
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class TestSuitesPluginTest {

    @Test
    void "applying plugin creates test common and unit directories and configures sourceSets"(@TempDir Path tempDir) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        File commonJava = new File(project.projectDir, 'src/test/common/java')
        File commonResources = new File(project.projectDir, 'src/test/common/resources')
        File unitJava = new File(project.projectDir, 'src/test/unit/java')
        File unitResources = new File(project.projectDir, 'src/test/unit/resources')

        assertFalse(commonJava.exists())
        assertFalse(commonResources.exists())
        assertFalse(unitJava.exists())
        assertFalse(unitResources.exists())

        project.pluginManager.apply('net.miwashi.test-suites')

        assertTrue(commonJava.exists() && commonJava.isDirectory())
        assertTrue(commonResources.exists() && commonResources.isDirectory())
        assertTrue(unitJava.exists() && unitJava.isDirectory())
        assertTrue(unitResources.exists() && unitResources.isDirectory())

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        def testCommon = sourceSets.findByName('testCommon')
        assertNotNull(testCommon)
        assertEquals(Collections.singleton(commonJava.canonicalFile), testCommon.java.srcDirs.collect { it.canonicalFile } as Set)
        assertEquals(Collections.singleton(commonResources.canonicalFile), testCommon.resources.srcDirs.collect { it.canonicalFile } as Set)

        def test = sourceSets.findByName('test')
        assertNotNull(test)
        assertEquals(Collections.singleton(unitJava.canonicalFile), test.java.srcDirs.collect { it.canonicalFile } as Set)
        assertEquals(Collections.singleton(unitResources.canonicalFile), test.resources.srcDirs.collect { it.canonicalFile } as Set)

        // Verify configurations wiring
        def testCommonImpl = project.configurations.getByName('testCommonImplementation')
        assertTrue(testCommonImpl.extendsFrom.contains(project.configurations.getByName('testImplementation')))

        def testCommonRuntime = project.configurations.getByName('testCommonRuntimeOnly')
        assertTrue(testCommonRuntime.extendsFrom.contains(project.configurations.getByName('testRuntimeOnly')))
    }
}
