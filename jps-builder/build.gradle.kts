tasks.jar {
    archiveFileName.set("jps-builder.jar")
}

tasks.compileTestJava {
    dependsOn(":jps-shared:composedJar")
}

tasks.compileJava {
    dependsOn(":jps-shared:composedJar")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    include("**/*Test.class")
}

dependencies {
    implementation(project(":jps-shared"))
}
