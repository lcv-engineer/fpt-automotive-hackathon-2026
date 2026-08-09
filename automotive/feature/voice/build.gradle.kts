import java.io.BufferedInputStream
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun downloadAndUnpackVoskModel(
    zipName: String,
    unpackedFolderName: String,
    destDir: java.io.File,
) {
    val marker = destDir.resolve("conf/model.conf")
    if (marker.exists()) {
        println("Vosk model already present at ${destDir.absolutePath}")
        return
    }
    val url = "https://alphacephei.com/vosk/models/$zipName"
    val zipFile = destDir.parentFile.resolve(zipName)
    destDir.parentFile.mkdirs()
    println("Downloading Vosk model from $url …")
    URI(url).toURL().openStream().use { input ->
        zipFile.outputStream().use { output -> input.copyTo(output) }
    }
    val extractRoot = destDir.parentFile.resolve("vosk-model-unpack-$unpackedFolderName")
    if (extractRoot.exists()) extractRoot.deleteRecursively()
    extractRoot.mkdirs()
    ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val target = extractRoot.resolve(entry.name)
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                target.outputStream().use { output -> zis.copyTo(output) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    val unpacked = extractRoot.resolve(unpackedFolderName)
    require(unpacked.resolve("conf/model.conf").exists()) {
        "Failed to unpack Vosk model $zipName"
    }
    if (destDir.exists()) destDir.deleteRecursively()
    require(unpacked.renameTo(destDir)) { "Failed to move $unpacked → $destDir" }
    zipFile.delete()
    extractRoot.deleteRecursively()
    println("Vosk model saved to ${destDir.absolutePath}")
}

val voskEnDir = layout.projectDirectory.dir("src/main/assets/model-en-us")
val voskViDir = layout.projectDirectory.dir("src/main/assets/model-vi")

val downloadVoskEnModel by tasks.registering {
    group = "vosk"
    description = "Download Vosk small en-US model into assets if missing"
    notCompatibleWithConfigurationCache("Downloads Vosk model over the network")
    outputs.file(voskEnDir.file("conf/model.conf"))
    doLast {
        downloadAndUnpackVoskModel(
            zipName = "vosk-model-small-en-us-0.15.zip",
            unpackedFolderName = "vosk-model-small-en-us-0.15",
            destDir = voskEnDir.asFile,
        )
    }
}

val downloadVoskViModel by tasks.registering {
    group = "vosk"
    description = "Download Vosk Vietnamese vn-0.4 model into assets if missing"
    notCompatibleWithConfigurationCache("Downloads Vosk model over the network")
    outputs.file(voskViDir.file("conf/model.conf"))
    doLast {
        downloadAndUnpackVoskModel(
            zipName = "vosk-model-vn-0.4.zip",
            unpackedFolderName = "vosk-model-vn-0.4",
            destDir = voskViDir.asFile,
        )
    }
}

val embeddingDir = layout.projectDirectory.dir("src/main/assets/embeddings")
val embeddingModel = embeddingDir.file("model_quantized.onnx")
val embeddingVocab = embeddingDir.file("vocab.txt")
val embeddingSource = embeddingDir.file(".source")
// distiluse-base-multilingual-cased-v2, not all-MiniLM-L6-v2: MiniLM's vocab is
// English WordPiece and tokenises accented Vietnamese into pure [UNK]. The
// encoder already refuses to embed an all-[UNK] sentence, which made tier T1
// return null for most real input -- safe, but absent. See the comment in
// OnnxEmbeddingEncoder.embed().
val embeddingModelUrl =
    "https://huggingface.co/Xenova/distiluse-base-multilingual-cased-v2/resolve/main/onnx/model_quantized.onnx"
val embeddingVocabUrl =
    "https://huggingface.co/Xenova/distiluse-base-multilingual-cased-v2/resolve/main/vocab.txt"
val embeddingSourceId = "$embeddingModelUrl\n$embeddingVocabUrl\n"

val downloadEmbeddingModel by tasks.registering {
    group = "embedding"
    description = "Download multilingual sentence-embedding ONNX + vocab into assets if missing"
    notCompatibleWithConfigurationCache("Downloads embedding model over the network")
    outputs.files(embeddingModel, embeddingVocab, embeddingSource)
    doLast {
        val dir = embeddingDir.asFile
        dir.mkdirs()
        val modelFile = embeddingModel.asFile
        val vocabFile = embeddingVocab.asFile
        val sourceFile = embeddingSource.asFile
        // A URL change must invalidate already-downloaded ignored assets. File
        // existence alone left existing worktrees silently running MiniLM.
        val sourceChanged = !sourceFile.exists() || sourceFile.readText() != embeddingSourceId
        if (sourceChanged || !modelFile.exists()) {
            println("Downloading multilingual embedding ONNX from $embeddingModelUrl …")
            URI(embeddingModelUrl).toURL().openStream().use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (sourceChanged || !vocabFile.exists()) {
            println("Downloading multilingual embedding vocab from $embeddingVocabUrl …")
            URI(embeddingVocabUrl).toURL().openStream().use { input ->
                vocabFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        sourceFile.writeText(embeddingSourceId)
        println("Embedding assets ready in ${dir.absolutePath}")
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadVoskEnModel, downloadVoskViModel, downloadEmbeddingModel)
}

android {
    namespace = "com.sopa.viva_automotive.feature.voice"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 32
        buildConfigField(
            "String",
            "ASR_ENGINE",
            "\"" + (project.findProperty("vivaAsrEngine") ?: "vosk") + "\"",
        )
        buildConfigField(
            "String",
            "ASR_BASE_URL",
            "\"" + (project.findProperty("vivaAsrBaseUrl") ?: "http://127.0.0.1:8080") + "\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "onnx"
    }
}

dependencies {
    implementation(project(":voice-core"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    implementation(project(":feature:media"))
    implementation(project(":vehicle-service:api"))

    implementation(libs.vosk.android)
    implementation(libs.onnxruntime.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.media)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
