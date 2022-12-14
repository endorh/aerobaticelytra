import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import java.text.SimpleDateFormat
import java.util.*

buildscript {
	repositories {
		maven("https://files.minecraftforge.net/maven")
		mavenCentral()
	}
	dependencies {
		classpath("net.minecraftforge.gradle:ForgeGradle:5.1.+") {
			isChanging = true
		}
	}
}

// Plugins
plugins {
	java
	id("net.minecraftforge.gradle")
	`maven-publish`
	// id("com.github.johnrengelman.shadow") version "7.1.2"
}

// Mod info --------------------------------------------------------------------

val modId = "aerobaticelytra"
val modGroup = "endorh.aerobaticelytra"
val githubRepo = "endorh/aerobatic-elytra"
val modVersion = "1.0.0"
val mcVersion = "1.18.2"
val forge = "40.1.0"
val forgeVersion = "$mcVersion-$forge"
val mappingsChannel = "official"
val mappingsVersion = "1.18.2"

group = modGroup
version = modVersion
val groupSlashed = modGroup.replace(".", "/")
val className = "AerobaticElytra"
val modArtifactId = "$modId-$mcVersion"
val modMavenArtifact = "$modGroup:$modArtifactId:$modVersion"

// Attributes
val displayName = "Aerobatic Elytra"
val vendor = "Endor H"
val credits = ""
val authors = "Endor H"
val issueTracker = "https://github.com/$githubRepo/issues"
val page = "https://www.curseforge.com/minecraft/mc-mods/aerobatic-elytra"
val updateJson = "https://github.com/$githubRepo/raw/updates/updates.json"
val logoFile = "$modId.png"
val modDescription = """
	Adds an special elytra able to roll, fly and leave a trail, like an aerobatic plane.
	All recipes and elytra upgrades can be modified by datapacks with great flexibility.
	Other mods may register their own flight modes for the elytra.
""".trimIndent()

// License
val license = "LGPL"

// Dependencies
val mixinVersion = "0.8.2"
val minimalMixinVersion = "0.7.10"
val flightCoreVersion = "1.0.+"
val simpleConfigVersion = "1.0.+"
val lazuLibVersion = "1.0.+"

// Integration
val jeiVersion = "9.7.1.255"
val curiosVersion = "1.18.2-5.0.7.1"
val caelusVersion = "1.18.1-3.0.0.2"
val aerobaticElytraJetpackVersion = "1.0.+"

val jarAttributes = mapOf(
	"Specification-Title"      to modId,
	"Specification-Vendor"     to vendor,
	"Specification-Version"    to "1",
	"Implementation-Title"     to project.name,
	"Implementation-Version"   to version,
	"Implementation-Vendor"    to vendor,
	"Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date()),
	"Maven-Artifact"           to modMavenArtifact
)

val modProperties = mapOf(
	"modid"         to modId,
	"display"       to displayName,
	"version"       to modVersion,
	"mcversion"     to mcVersion,
	"mixinver"      to mixinVersion,
	"minmixin"      to minimalMixinVersion,
	"vendor"        to vendor,
	"authors"       to authors,
	"credits"       to credits,
	"license"       to license,
	"page"          to page,
	"issue_tracker" to issueTracker,
	"update_json"   to updateJson,
	"logo_file"     to logoFile,
	"description"   to modDescription,
	"group"         to group,
	"class_name"    to className,
	"group_slashed" to groupSlashed,
)

// Source Sets -----------------------------------------------------------------

sourceSets.main.get().resources {
	// Include resources generated by data generators.
	srcDir("src/generated/resources")
}

// Java options ----------------------------------------------------------------

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
}

println(
	"Java: " + System.getProperty("java.version")
	+ " JVM: " + System.getProperty("java.vm.version") + "(" + System.getProperty("java.vendor")
	+ ") Arch: " + System.getProperty("os.arch"))

println("Mod: \"$displayName\" ($modId), version: $mcVersion-$modVersion (Forge: $forge)")

// Minecraft options -----------------------------------------------------------

minecraft {
	mappings(mappingsChannel, mappingsVersion)
	
	// Run configurations
	runs {
		val client = create("client") {
			workingDirectory(file("run"))
			
			// Allowed flags: SCAN, REGISTRIES, REGISTRYDUMP
			property("forge.logging.markers", "REGISTRIES")
			property("forge.logging.console.level", "debug")
			property("mixin.env.disableRefMap", "true")
			
			// JetBrains Runtime HotSwap (run with vanilla JBR 17 without fast-debug, see CONTRIBUTING.md)
			jvmArg("-XX:+AllowEnhancedClassRedefinition")
			
			mods {
				create(modId) {
					source(sourceSets.main.get())
				}
			}
		}
		
		create("server") {
			workingDirectory(file("run"))
			
			// Allowed flags: SCAN, REGISTRIES, REGISTRYDUMP
			property("forge.logging.markers", "REGISTRIES")
			property("forge.logging.console.level", "debug")
			property("mixin.env.disableRefMap", "true")
			
			// JetBrains Runtime HotSwap (run with vanilla JBR 17 without fast-debug, see CONTRIBUTING.md)
			jvmArg("-XX:+AllowEnhancedClassRedefinition")
			
			arg("nogui")
			
			mods {
				create(modId) {
					source(sourceSets.main.get())
				}
			}
		}
		
		create("client2") {
			parent(client)
			args("--username", "Dev2")
		}
	}
}

// Dependencies ----------------------------------------------------------------

val explicitGroups: MutableSet<String> = mutableSetOf()
fun MavenArtifactRepository.includeOnly(vararg groups: String) {
	content {
		groups.forEach {
			// Include subgroups as well
			val regex = "${it.replace(".", "\\.")}(\\..*)?"
			includeGroupByRegex(regex)
			explicitGroups.add(regex)
		}
	}
}
fun MavenArtifactRepository.excludeExplicit() {
	content {
		explicitGroups.forEach {
			excludeGroupByRegex(it)
		}
	}
}

repositories {
	maven("https://www.cursemaven.com") {
		name = "Curse Maven"
		includeOnly("curse.maven")
	}
	
	maven("https://dvs1.progwml6.com/files/maven/") {
		name = "Progwml6 maven" // JEI
		includeOnly("mezz")
	}
	maven("https://modmaven.k-4u.nl") {
		name = "ModMaven" // JEI fallback
		includeOnly("mezz")
	}
	
	maven("https://maven.theillusivec4.top/") {
		name = "TheIllusiveC4" // Curios API
		includeOnly("top.theillusivec4")
	}
	
	// Local repository for faster multi-mod development
	maven(rootProject.projectDir.parentFile.resolve("maven")) {
		name = "LocalMods"
		includeOnly("endorh")
	}
	
	// GitHub Packages
	val gitHubRepos = mapOf(
		"endorh/lazulib" to "endorh.util.lazulib",
		"endorh/flight-core" to "endorh.flightcore",
		"endorh/simple-config" to "endorh.simpleconfig",
	)
	for (repo in gitHubRepos.entries) maven("https://maven.pkg.github.com/${repo.key}") {
		name = "GitHub/${repo.key}"
		includeOnly(repo.value)
		credentials {
			username = "gradle" // Not relevant, must not be empty
			// read:packages only GitHub token published by Endor H
			// You may as well use your own GitHub PAT with read:packages scope, until GitHub
			//   supports unauthenticated read access to public packages, see:
			//   https://github.com/orgs/community/discussions/26634#discussioncomment-3252637
			password = "\u0067hp_SjEzHOWgAWIKVczipKZzLPPJcCMHHd1LILfK"
		}
	}
	
	maven("https://repo.maven.apache.org/maven2") {
		name = "Maven Central"
		excludeExplicit()
	}
}

dependencies {
	// IDEε
    implementation("org.junit.jupiter:junit-jupiter:5.9.0")
	implementation("org.jetbrains:annotations:23.0.0")

	// Minecraft
    minecraft("net.minecraftforge:forge:$forgeVersion")

	// Mod dependencies
	// Flight Core
	implementation("endorh.flightcore:flightcore-$mcVersion:$flightCoreVersion:deobf")

	// Simple Config
	compileOnly("endorh.simpleconfig:simpleconfig-$mcVersion:$simpleConfigVersion:api")
	runtimeOnly(fg.deobf("endorh.simpleconfig:simpleconfig-$mcVersion:$simpleConfigVersion"))

	// LazuLib
	implementation(fg.deobf("endorh.util.lazulib:lazulib-$mcVersion:$lazuLibVersion"))
	
	// Mod integrations --------------------------------------------------------
	// JEI
	compileOnly(fg.deobf("mezz.jei:jei-$mcVersion:$jeiVersion:api"))
	runtimeOnly(fg.deobf("mezz.jei:jei-$mcVersion:$jeiVersion"))

	// Curios API
	compileOnly(fg.deobf("top.theillusivec4.curios:curios-forge:$curiosVersion:api"))
	runtimeOnly(fg.deobf("top.theillusivec4.curios:curios-forge:$curiosVersion"))

	// Caelus API
	compileOnly(fg.deobf("top.theillusivec4.caelus:caelus-forge:$caelusVersion:api"))
	runtimeOnly(fg.deobf("top.theillusivec4.caelus:caelus-forge:$caelusVersion"))

	// Used for debug ----------------------------------------------------------
	// Aerobatic Elytra Jetpack
	// runtimeOnly(fg.deobf("endorh.aerobaticelytra.jetpack:aerobaticelytrajetpack-$mcVersion:$aerobaticElytraJetpackVersion"))

	// Curious Elytra
	runtimeOnly(fg.deobf("curse.maven:elytra-slot-317716:3601975"))

	// Colytra
	runtimeOnly(fg.deobf("curse.maven:colytra-280200:3725170"))

	// Customizable Elytra
	runtimeOnly(fg.deobf("curse.maven:customizableelytra-440047:3728574"))

	// Additional Banners
	runtimeOnly(fg.deobf("curse.maven:bookshelf-228525:3900932"))
	runtimeOnly(fg.deobf("curse.maven:additionalbanners-230137:3835686"))

	// Xaero's World Map
	runtimeOnly(fg.deobf("curse.maven:xaeros-worldmap-317780:3948203"))

	// Xaero's Minimap (waypoint rendering doesn't account for camera roll)
	// runtimeOnly(fg.deobf("curse.maven:xaeros-minimap-263420:3937634"))

	// Immersive Portals (untestable in an unobfuscated environment, crashes without refmaps)
	//   Portals with rotation override roll with a fixed animation that is sometimes in the wrong axis
	//   Wings of players in the portal frontier bind the wrong texture when rendering
	// runtimeOnly(fg.deobf("curse.maven:immersive-portals-355440:unreleased"))

	// Catalogue
	runtimeOnly(fg.deobf("curse.maven:catalogue-459701:3803098"))
}

// Tasks -----------------------------------------------------------------------

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.classes {
	dependsOn(tasks.extractNatives.get())
}

lateinit var reobfJar: RenameJarInPlace
reobf {
	reobfJar = create("jar")
}

// Jar attributes
tasks.jar {
	archiveBaseName.set(modArtifactId)
	
	manifest {
		attributes(jarAttributes)
	}

	finalizedBy(reobfJar)
}

val sourcesJarTask = tasks.register<Jar>("sourcesJar") {
	group = "build"
	archiveBaseName.set(modArtifactId)
	archiveClassifier.set("sources")
	
	from(sourceSets.main.get().allJava)
	
	manifest {
		attributes(jarAttributes)
		attributes(mapOf("Maven-Artifact" to "$modMavenArtifact:${archiveClassifier.get()}"))
	}
}

val deobfJarTask = tasks.register<Jar>("deobfJar") {
	group = "build"
	archiveBaseName.set(modArtifactId)
	archiveClassifier.set("deobf")
	
	from(sourceSets.main.get().output)
	
	manifest {
		attributes(jarAttributes)
		attributes(mapOf("Maven-Artifact" to "$modMavenArtifact:${archiveClassifier.get()}"))
	}
}

// Process resources
tasks.processResources {
	inputs.properties(modProperties)
	duplicatesStrategy = DuplicatesStrategy.INCLUDE
	
	// Exclude development files
	exclude("**/.dev/**")
	
	from(sourceSets.main.get().resources.srcDirs) {
		// Expand properties in manifest files
		filesMatching(listOf("**/*.toml", "**/*.mcmeta")) {
			expand(modProperties)
		}
		// Expand properties in JSON resources except for translations
		filesMatching("**/*.json") {
			if (!path.contains("/lang/"))
				expand(modProperties)
		}
	}
}

val saveModsTask = tasks.register<Copy>("saveMods") {
	from("run/mods")
	into("saves/mods")
}

val setupMinecraftTask = tasks.register<Copy>("setupMinecraft") {
	from("saves")
	into("run")
}

val cleanBuildAssetsTask = tasks.register<Delete>("cleanBuildAssets") {
	delete("build/resources/main/assets")
}

// Make the clean task remove the run and logs folder
tasks.clean {
	delete("run")
	delete("logs")
	dependsOn(saveModsTask)
	dependsOn(cleanBuildAssetsTask)
	finalizedBy(setupMinecraftTask)
}

// Publishing ------------------------------------------------------------------

artifacts {
	archives(tasks.jar.get())
	archives(sourcesJarTask)
	archives(deobfJarTask)
}

publishing {
	repositories {
		maven("https://maven.pkg.github.com/$githubRepo") {
			name = "GitHubPackages"
			credentials {
				username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
				password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
			}
		}
		
		maven(rootProject.projectDir.parentFile.resolve("maven")) {
			name = "LocalMods"
		}
	}
	
	publications {
		register<MavenPublication>("mod") {
			artifactId = "$modId-$mcVersion"
			version = modVersion
			
			artifact(tasks.jar.get())
			artifact(sourcesJarTask)
			artifact(deobfJarTask)
			
			pom {
				name.set(displayName)
				url.set(page)
				description.set(modDescription)
			}
		}
	}
}