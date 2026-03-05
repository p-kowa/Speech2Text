package com.example.speech2text

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleHelper {
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    /**
     * Muss in onCreate() der Activity aufgerufen werden
     */
    fun registerSignInLauncher(
        activity: AppCompatActivity,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onFailure: (String) -> Unit
    ) {
        signInLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    onSuccess(account)
                } catch (e: ApiException) {
                    onFailure("Sign-In failed: ${e.message}")
                }
            } else {
                onFailure("Sign-In cancelled")
            }
        }
    }

    /**
     * Startet den kompletten Upload-Prozess
     */
    fun uploadTrainingData(
        activity: AppCompatActivity,
        trainingDataFile: java.io.File,
        audioFiles: List<java.io.File>,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = activity,
            onAuthenticated = { account ->
                // Bereits angemeldet → Upload starten
                performUpload(activity, account, trainingDataFile, audioFiles, onProgress, onSuccess, onError)
            },
            onError = { _ ->
                // Nicht angemeldet → Sign-In starten
                startSignIn(activity)
            }
        )
    }

    /**
     * Upload mit Ordner-Organisation
     */
    fun uploadTrainingDataToFolder(
        activity: AppCompatActivity,
        trainingDataFile: java.io.File,
        audioFiles: List<java.io.File>,
        folderName: String = "TTS_Voice_Samples",
        onProgress: (String) -> Unit,
        onSuccess: (String) -> Unit, // Gibt Folder-ID zurück
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = activity,
            onAuthenticated = { account ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        onProgress("📁 Checking/Creating folder...")

                        val credential = GoogleAccountCredential.usingOAuth2(
                            activity,
                            listOf(DriveScopes.DRIVE_FILE)
                        ).apply {
                            selectedAccount = account.account
                        }

                        val driveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("Speech2Text").build()

                        // Ordner erstellen oder ID abrufen
                        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
                        val existingFolders = driveService.files().list()
                            .setQ(query)
                            .setFields("files(id, name)")
                            .setPageSize(1)
                            .execute()

                        val folderId = if (existingFolders.files.isNotEmpty()) {
                            android.util.Log.d("GoogleHelper", "📁 Using existing folder: ${existingFolders.files[0].id}")
                            existingFolders.files[0].id
                        } else {
                            val folderMetadata = com.google.api.services.drive.model.File().apply {
                                name = folderName
                                mimeType = "application/vnd.google-apps.folder"
                            }
                            val newFolder = driveService.files().create(folderMetadata)
                                .setFields("id")
                                .execute()
                            android.util.Log.d("GoogleHelper", "✅ Created new folder: ${newFolder.id}")
                            newFolder.id
                        }

                        val uploadedFiles = mutableListOf<String>()

                        // Upload CSV
                        withContext(Dispatchers.Main) { onProgress("📤 Uploading CSV...") }
                        val csvFileId = uploadFileToDrive(driveService, trainingDataFile, folderId)
                        uploadedFiles.add("CSV: ${trainingDataFile.name}")

                        // Upload audio files
                        audioFiles.forEachIndexed { index, audioFile ->
                            withContext(Dispatchers.Main) {
                                onProgress("🎤 Uploading Audio ${index + 1}/${audioFiles.size}...")
                            }
                            uploadFileToDrive(driveService, audioFile, folderId)
                            uploadedFiles.add("Audio: ${audioFile.name}")
                        }

                        withContext(Dispatchers.Main) {
                            android.util.Log.d("GoogleHelper", "✅ Upload successful to folder: $folderId")
                            uploadedFiles.forEach { android.util.Log.d("GoogleHelper", "  - $it") }
                            onSuccess(folderId)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            android.util.Log.e("GoogleHelper", "❌ Upload failed: ${e.message}", e)
                            onError("Upload failed: ${e.message}")
                        }
                    }
                }
            },
            onError = { _ ->
                startSignIn(activity)
            }
        )
    }

    private fun startSignIn(context: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun performUpload(
        context: Context,
        account: GoogleSignInAccount,
        trainingDataFile: java.io.File,
        audioFiles: List<java.io.File>,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {CoroutineScope(Dispatchers.IO).launch {
        try {
            onProgress("Connecting to Google Drive...")

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Speech2Text").build()

            val uploadedFiles = mutableListOf<String>()

            // Upload CSV
            onProgress("📤 Uploading CSV...")
            val csvFileId = uploadFileToDrive(driveService, trainingDataFile)
            uploadedFiles.add("CSV: ${trainingDataFile.name} (ID: $csvFileId)")

            // Upload audio files
            audioFiles.forEachIndexed { index, audioFile ->
                onProgress("🎤 Uploading Audio ${index + 1}/${audioFiles.size}...")
                val audioFileId = uploadFileToDrive(driveService, audioFile)
                uploadedFiles.add("Audio: ${audioFile.name} (ID: $audioFileId)")
            }

            withContext(Dispatchers.Main) {
                android.util.Log.d("GoogleHelper", "✅ Upload successful!")
                uploadedFiles.forEach { android.util.Log.d("GoogleHelper", "  - $it") }
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.util.Log.e("GoogleHelper", "❌ Upload failed: ${e.message}", e)
                onError("Upload failed: ${e.message}")
            }
        }
    }
    }

    private fun uploadFileToDrive(driveService: Drive, file: java.io.File, folderId: String? = null): String {
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = file.name
            // If folderId is provided, upload file to this folder
            if (folderId != null) {
                parents = listOf(folderId)
            }
        }

        val mediaContent = FileContent("application/octet-stream", file)

        val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id, name, webViewLink")
            .execute()

        android.util.Log.d("GoogleHelper", "✅ Uploaded: ${uploadedFile.name} - Link: ${uploadedFile.webViewLink}")
        return uploadedFile.id
    }

    // Deine bestehende checkAuthentication-Methode bleibt
    fun checkAuthentication(
        context: Context,
        onAuthenticated: (GoogleSignInAccount) -> Unit,
        onError: (String) -> Unit
    ) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            onAuthenticated(account)
        } else {
            onError("Not authenticated")
        }
    }

    fun listDriveFiles(
        context: Context,
        onSuccess: (List<DriveFileInfo>) -> Unit,
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = context,
            onAuthenticated = { account ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context,
                            listOf(DriveScopes.DRIVE_FILE)
                        ).apply {
                            selectedAccount = account.account
                        }

                        val driveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("Speech2Text").build()

                        val result = driveService.files().list()
                            .setPageSize(100)
                            .setFields("files(id, name, mimeType, createdTime, size)")
                            .setOrderBy("createdTime desc")
                            .execute()

                        val files = result.files.map { file ->
                            DriveFileInfo(
                                id = file.id,
                                name = file.name,
                                mimeType = file.mimeType,
                                isFolder = file.mimeType == "application/vnd.google-apps.folder",
                                createdTime = file.createdTime?.toString() ?: "Unknown",
                                size = file.getSize() ?: 0L,
                                webViewLink = "https://drive.google.com/file/d/${file.id}/view"
                            )
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess(files)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Error loading files: ${e.message}")
                        }
                    }
                }
            },
            onError = onError
        )
    }

    /**
     * Listet Dateien in einem bestimmten Ordner
     */
    fun listFilesInFolder(
        context: Context,
        folderId: String,
        onSuccess: (List<DriveFileInfo>) -> Unit,
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = context,
            onAuthenticated = { account ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context,
                            listOf(DriveScopes.DRIVE_FILE)
                        ).apply {
                            selectedAccount = account.account
                        }

                        val driveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("Speech2Text").build()

                        val query = "'$folderId' in parents and trashed = false"
                        val result = driveService.files().list()
                            .setQ(query)
                            .setPageSize(100)
                            .setFields("files(id, name, mimeType, createdTime, size)")
                            .setOrderBy("name")
                            .execute()

                        val files = result.files.map { file ->
                            DriveFileInfo(
                                id = file.id,
                                name = file.name,
                                mimeType = file.mimeType,
                                isFolder = file.mimeType == "application/vnd.google-apps.folder",
                                createdTime = file.createdTime?.toString() ?: "Unknown",
                                size = file.getSize() ?: 0L,
                                webViewLink = "https://drive.google.com/file/d/${file.id}/view"
                            )
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess(files)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Fehler beim Laden: ${e.message}")
                        }
                    }
                }
            },
            onError = onError
        )
    }

    fun checkIfFolderExists(
        context: Context,
        folderName: String,
        onResult: (Boolean, String?) -> Unit,
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = context,
            onAuthenticated = { account ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context,
                            listOf(DriveScopes.DRIVE_FILE)
                        ).apply {
                            selectedAccount = account.account
                        }

                        val driveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("Speech2Text").build()

                        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
                        val result = driveService.files().list()
                            .setQ(query)
                            .setFields("files(id, name)")
                            .execute()

                        if (result.files.isNotEmpty()) {
                            val folderId = result.files[0].id
                            withContext(Dispatchers.Main) {
                                onResult(true, folderId)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onResult(false, null)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Error searching for folder: ${e.message}")
                        }
                    }
                }
            },
            onError = onError
        )
    }

    fun createFolder(
        context: Context,
        folderName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        checkAuthentication(
            context = context,
            onAuthenticated = { account ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val credential = GoogleAccountCredential.usingOAuth2(
                            context,
                            listOf(DriveScopes.DRIVE_FILE)
                        ).apply {
                            selectedAccount = account.account
                        }

                        val driveService = Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("Speech2Text").build()

                        // Erst prüfen, ob der Ordner bereits existiert
                        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
                        val existingFolders = driveService.files().list()
                            .setQ(query)
                            .setFields("files(id, name)")
                            .setPageSize(1)
                            .execute()

                        val folderId = if (existingFolders.files.isNotEmpty()) {
                            // Ordner existiert bereits
                            android.util.Log.d("GoogleHelper", "📁 Folder '$folderName' already exists: ${existingFolders.files[0].id}")
                            existingFolders.files[0].id
                        } else {
                            // Ordner erstellen
                            val fileMetadata = com.google.api.services.drive.model.File().apply {
                                name = folderName
                                mimeType = "application/vnd.google-apps.folder"
                            }

                            val folder = driveService.files().create(fileMetadata)
                                .setFields("id")
                                .execute()

                            android.util.Log.d("GoogleHelper", "✅ Created new folder '$folderName': ${folder.id}")
                            folder.id
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess(folderId)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Error creating folder: ${e.message}")
                        }
                    }
                }
            },
            onError = onError
        )
    }
}

// Data Class for file information
data class DriveFileInfo(
    val id: String,
    val name: String,
    val mimeType: String,
    val isFolder: Boolean,
    val createdTime: String,
    val size: Long,
    val webViewLink: String
)

