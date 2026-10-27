plugins { java }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Surum bulunamazsa: "26.3.build.+" dene
    compileOnly("io.papermc.paper:paper-api:26.3.build.218-stable")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.jar { archiveFileName.set("BGPs_Vanilla_Plus_Addon.jar") }
