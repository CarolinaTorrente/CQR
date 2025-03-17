package com.example.cinequizroyale

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.cinequizroyale.ui.theme.AccentRed
import com.example.cinequizroyale.ui.theme.BackgroundDark
import com.example.cinequizroyale.ui.theme.ButtonBg
import com.example.cinequizroyale.ui.theme.CinequizroyaleTheme
import com.example.cinequizroyale.ui.theme.PrimaryText
import com.example.cinequizroyale.ui.theme.SecondaryText
import com.example.cinequizroyale.ui.theme.SurfaceDark
import com.example.cinequizroyale.utils.QuestionManager
import com.example.cinequizroyale.utils.QuestionUploader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.storage.StorageScopes
import com.google.api.services.storage.model.StorageObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import android.location.Location
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date


class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private lateinit var googleSignInClient: GoogleSignInClient
    private var currentAccount by mutableStateOf<GoogleSignInAccount?>(null)
    private lateinit var questionManager: QuestionManager

    // Add user points state
    private var userPoints by mutableStateOf(100)

    // Activity result launcher for Google Sign-In
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        }
    }

    private var redemptionHistory by mutableStateOf<List<RedemptionItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize CloudStorageProvider
        CloudStorageProvider.initialize(this)

        // Initialize QuestionManager
        questionManager = QuestionManager(this)

        // Configure Google Sign In with Cloud Storage scope
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .requestProfile()
            // Add Cloud Storage scope
            .requestScopes(Scope(StorageScopes.DEVSTORAGE_READ_WRITE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        currentAccount = account

        // Load user data if logged in
        if (account != null) {
            loadUserData(
                account,
                onPointsLoaded = { points -> userPoints = points },
                onRedemptionsLoaded = { redemptions -> redemptionHistory = redemptions }
            )
        }

        setContent {
            CinequizroyaleTheme {
                val currentScreen = remember { mutableStateOf("main") }

                when {
                    currentAccount == null -> {
                        LoginScreen(onGoogleSignIn = { signInWithGoogle() })
                    }
                    currentScreen.value == "main" -> {
                        MainScreen(
                            account = currentAccount!!,
                            userPoints = userPoints,
                            onLogout = { signOut() },
                            onPlayClick = { currentScreen.value = "questions" },
                            onPrizesClick = { currentScreen.value = "prizes" },
                            onHistoryClick = { currentScreen.value = "history" },
                            onCinemasClick = { currentScreen.value = "cinemas" }
                        )
                    }
                    currentScreen.value == "questions" -> {
                        CinemaQuestionsScreen(
                            account = currentAccount!!,
                            onBack = { currentScreen.value = "main" },
                            onQuizComplete = { points -> updateUserPoints(points) }
                        )
                    }
                    currentScreen.value == "prizes" -> {
                        RedeemPrizesScreen(
                            userPoints = userPoints,
                            onBack = { currentScreen.value = "main" },
                            onRedeemPrize = { prize ->
                                redeemPrize(prize)
                                // After redemption, go back to main screen
                                currentScreen.value = "main"
                            }
                        )
                    }
                    currentScreen.value == "history" -> {
                        RedemptionHistoryScreen(
                            redemptions = redemptionHistory,
                            onBack = { currentScreen.value = "main" }
                        )
                    }
                    currentScreen.value == "cinemas" -> {
                        NearestCinemasScreen(
                            onBack = { currentScreen.value = "main" }
                        )
                    }
                }
            }
        }
    }

    private fun loadUserData(
        account: GoogleSignInAccount,
        onPointsLoaded: (Int) -> Unit,
        onRedemptionsLoaded: (List<RedemptionItem>) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Set up credentials for Cloud Storage
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity,
                    listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
                )
                credential.selectedAccount = account.account

                // Set up Cloud Storage client
                val storage = Storage.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("CineQuizRoyaleB")
                    .build()

                // Get user data from Cloud Storage
                val userDataStream = storage.objects().get("cinequizroyale-bucket-2", "users/${account.id}.json")
                    .executeMedia()
                    .content

                val userData = userDataStream.bufferedReader().use { it.readText() }

                // Parse JSON
                val jsonObject = JSONObject(userData)

                // Get points
                if (jsonObject.has("points")) {
                    val points = jsonObject.getInt("points")
                    withContext(Dispatchers.Main) {
                        onPointsLoaded(points)
                    }
                }

                // Get redemption history
                val redemptions = mutableListOf<RedemptionItem>()
                if (jsonObject.has("redemptions")) {
                    val redemptionsArray = jsonObject.getJSONArray("redemptions")
                    for (i in 0 until redemptionsArray.length()) {
                        val redemptionObj = redemptionsArray.getJSONObject(i)
                        redemptions.add(
                            RedemptionItem(
                                id = redemptionObj.getString("id"),
                                name = redemptionObj.getString("name"),
                                pointsRequired = redemptionObj.getInt("pointsRequired"),
                                redeemedAt = redemptionObj.getString("redeemedAt")
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    onRedemptionsLoaded(redemptions)
                }

            } catch (e: Exception) {
                Log.e(tag, "Error loading user data: ${e.message}", e)
                // Keep default values if there's an error
            }
        }
    }

    // Function to load user points from Cloud Storage
    private fun loadUserPoints(account: GoogleSignInAccount) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Set up credentials for Cloud Storage
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity,
                    listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
                )
                credential.selectedAccount = account.account

                // Set up Cloud Storage client
                val storage = Storage.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("CineQuizRoyaleB")
                    .build()

                // Get user data from Cloud Storage
                val userDataStream = storage.objects().get("cinequizroyale-bucket-2", "users/${account.id}.json")
                    .executeMedia()
                    .content

                val userData = userDataStream.bufferedReader().use { it.readText() }

                // Parse JSON
                val jsonObject = JSONObject(userData)
                if (jsonObject.has("points")) {
                    val points = jsonObject.getInt("points")
                    withContext(Dispatchers.Main) {
                        userPoints = points
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Error loading user points: ${e.message}", e)
                // Keep default points value if there's an error
            }
        }
    }


    // Function to update user points
    private fun updateUserPoints(newPoints: Int) {
        currentAccount?.let { account ->
            // Update local points
            userPoints += newPoints

            // Update points in Cloud Storage
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Set up credentials for Cloud Storage
                    val credential = GoogleAccountCredential.usingOAuth2(
                        this@MainActivity,
                        listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
                    )
                    credential.selectedAccount = account.account

                    // Set up Cloud Storage client
                    val storage = Storage.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    )
                        .setApplicationName("CineQuizRoyaleB")
                        .build()

                    // Get existing user data
                    val userDataStream = storage.objects().get("cinequizroyale-bucket-2", "users/${account.id}.json")
                        .executeMedia()
                        .content

                    val userData = userDataStream.bufferedReader().use { it.readText() }

                    // Parse and update JSON
                    val jsonObject = JSONObject(userData)
                    jsonObject.put("points", userPoints)

                    // Upload updated data
                    val inputStream = ByteArrayInputStream(jsonObject.toString().toByteArray())

                    // Create StorageObject metadata
                    val storageObject = StorageObject()
                        .setName("users/${account.id}.json")
                        .setContentType("application/json")

                    // Upload to Cloud Storage
                    storage.objects().insert(
                        "cinequizroyale-bucket-2",
                        storageObject,
                        InputStreamContent("application/json", inputStream)
                    ).execute()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Points updated successfully!", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Log.e(tag, "Error updating user points: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error updating points: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }


    // Function to redeem a prize
    private fun redeemPrize(prize: PrizeItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentAccount?.let { account ->
                    // Only deduct points if user has enough
                    if (userPoints >= prize.pointsRequired) {
                        // Deduct points
                        val newPoints = userPoints - prize.pointsRequired

                        // Update local points
                        withContext(Dispatchers.Main) {
                            userPoints = newPoints
                        }

                        // Update points in Cloud Storage
                        // Set up credentials for Cloud Storage
                        val credential = GoogleAccountCredential.usingOAuth2(
                            this@MainActivity,
                            listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
                        )
                        credential.selectedAccount = account.account

                        // Set up Cloud Storage client
                        val storage = Storage.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        )
                            .setApplicationName("CineQuizRoyaleB")
                            .build()

                        // Get existing user data
                        val userDataStream = storage.objects().get("cinequizroyale-bucket-2", "users/${account.id}.json")
                            .executeMedia()
                            .content

                        val userData = userDataStream.bufferedReader().use { it.readText() }

                        // Parse and update JSON
                        val jsonObject = JSONObject(userData)
                        jsonObject.put("points", newPoints)

                        // Add redemption history
                        val redemptionsArray = if (jsonObject.has("redemptions")) {
                            jsonObject.getJSONArray("redemptions")
                        } else {
                            JSONArray()
                        }

                        val redemption = JSONObject()
                        redemption.put("id", prize.id)
                        redemption.put("name", prize.name)
                        redemption.put("pointsRequired", prize.pointsRequired)
                        redemption.put("redeemedAt", Date().toString())

                        redemptionsArray.put(redemption)
                        jsonObject.put("redemptions", redemptionsArray)

                        // Upload updated data
                        val inputStream = ByteArrayInputStream(jsonObject.toString().toByteArray())

                        // Create StorageObject metadata
                        val storageObject = StorageObject()
                            .setName("users/${account.id}.json")
                            .setContentType("application/json")

                        // Upload to Cloud Storage
                        storage.objects().insert(
                            "cinequizroyale-bucket-2",
                            storageObject,
                            InputStreamContent("application/json", inputStream)
                        ).execute()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Prize redeemed successfully! Check your email for details.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Not enough points to redeem this prize.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error redeeming prize: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error redeeming prize: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            currentAccount = account

            // Save user data to Cloud Storage
            saveUserToCloudStorage(account)

            lifecycleScope.launch {
                try {
                    val questionsExist = checkIfQuestionsExist(account)
                    if (!questionsExist) {val success = QuestionUploader.uploadQuestions(this@MainActivity, account)
                        if (success) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Sample questions uploaded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error checking/uploading questions: ${e.message}", e)
                }
            }

        } catch (e: ApiException) {
            // Sign in failed
            Log.w(tag, "signInResult:failed code=" + e.statusCode)
            Toast.makeText(this, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            currentAccount = null
        }
    }


    private suspend fun checkIfQuestionsExist(account: GoogleSignInAccount): Boolean {
        return try {
            // Set up credentials for Cloud Storage
            val credential = GoogleAccountCredential.usingOAuth2(
                this@MainActivity,
                listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
            )
            credential.selectedAccount = account.account

            // Set up Cloud Storage client
            val storage = Storage.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("CineQuizRoyaleB")
                .build()

            // Check if questions file exists
            val objList = storage.objects().list("cinequizroyale-bucket-2")
                .setPrefix("questions/cinema_questions.json")
                .execute()

            objList.items?.isNotEmpty() ?: false

        } catch (e: Exception) {
            Log.e(tag, "Error checking if questions exist: ${e.message}", e)
            false
        }
    }

    private fun saveUserToCloudStorage(account: GoogleSignInAccount) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Set up credentials for Cloud Storage
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity,
                    listOf(StorageScopes.DEVSTORAGE_READ_WRITE)
                )
                credential.selectedAccount = account.account

                // Set up Cloud Storage client
                val storage = Storage.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("CineQuizRoyaleB")
                    .build()

                // Create user data JSON
                val userData = """
                {
                    "id": "${account.id}",
                    "name": "${account.displayName ?: ""}",
                    "email": "${account.email ?: ""}",
                    "photoUrl": "${account.photoUrl ?: ""}",
                    "lastLogin": "${Date()}",
                    "points": 0
                }
                    """.trimIndent()

                // Convert to input stream
                val inputStream = ByteArrayInputStream(userData.toByteArray())

                // Create StorageObject metadata
                val storageObject = StorageObject()
                    .setName("users/${account.id}.json")
                    .setContentType("application/json")

                // Upload to Cloud Storage
                storage.objects().insert(
                    "cinequizroyale-bucket-2",
                    storageObject,
                    InputStreamContent("application/json", inputStream)
                ).execute()

                // Switch to Main thread only for UI updates
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "User data saved to Cloud", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(tag, "Error saving user data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error saving user data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this) {
            currentAccount = null
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
        }
    }
}

// Let's also add a RedemptionHistoryScreen to view past redemptions
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedemptionHistoryScreen(
    redemptions: List<RedemptionItem>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Redemption History",
                        color = PrimaryText,
                        fontFamily = FontFamily(Font(R.font.luckiest_guy))
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.backarrow),
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (redemptions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No redemption history yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn {
                    items(redemptions) { redemption ->
                        RedemptionHistoryItem(redemption)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// Redemption history item data class
data class RedemptionItem(
    val id: String,
    val name: String,
    val pointsRequired: Int,
    val redeemedAt: String
)

// Individual redemption history item
@Composable
fun RedemptionHistoryItem(redemption: RedemptionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prize icon
            Image(
                painter = painterResource(id = R.drawable.photouser), // Use an appropriate redemption icon
                contentDescription = "Redemption",
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 16.dp)
            )

            // Redemption details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = redemption.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = "${redemption.pointsRequired} points",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Redeemed on: ${redemption.redeemedAt}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Let's also add a confirmation dialog for when a user tries to redeem a prize
@Composable
fun RedeemConfirmationDialog(
    prize: PrizeItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Confirm Redemption",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text("Are you sure you want to redeem:")
                Text(
                    text = prize.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text("This will deduct ${prize.pointsRequired} points from your account.")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@Composable
fun AppContent(
    isLoggedIn: Boolean,
    onGoogleSignIn: () -> Unit,
    onLogout: () -> Unit,
    account: GoogleSignInAccount?,
    questionManager: QuestionManager,
    userPoints: Int,
    onQuizComplete: (Int) -> Unit,
    onRedeemPrize: (PrizeItem) -> Unit
) {
    var currentScreen by remember { mutableStateOf("main") }

    when {
        !isLoggedIn -> {
            LoginScreen(onGoogleSignIn = onGoogleSignIn)
        }
        currentScreen == "main" -> {
            MainScreen(
                account = account!!,
                userPoints = userPoints,
                onLogout = onLogout,
                onPlayClick = { currentScreen = "questions" },
                onPrizesClick = { currentScreen = "prizes" },
                onHistoryClick = { currentScreen = "history" },
                onCinemasClick = { currentScreen = "cinemas" }
            )
        }
        currentScreen == "questions" -> {
            if (account != null) {
                CinemaQuestionsScreen(
                    account = account,
                    onBack = { currentScreen = "main" },
                    onQuizComplete = onQuizComplete
                )
            }
        }
        currentScreen == "prizes" -> {
            RedeemPrizesScreen(
                userPoints = userPoints,
                onBack = { currentScreen = "main" },
                onRedeemPrize = onRedeemPrize
            )
        }
    }
}

@Composable
fun LoginScreen(onGoogleSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(BackgroundDark),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CineQuiz Royale",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            fontFamily = FontFamily(Font(R.font.luckiest_guy))
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBg)
        ) {
            Text(
                text = "Sign in with Google",
                color = SecondaryText,
                fontFamily = FontFamily(Font(R.font.caveat))
            )
        }
    }
}

// we update the MainScreen function to include the onPrizesClick parameter:
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: GoogleSignInAccount,
    userPoints: Int,
    onLogout: () -> Unit,
    onPlayClick: () -> Unit,
    onPrizesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onCinemasClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hello ${account.displayName ?: "User"}!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                fontFamily = FontFamily(Font(R.font.luckiest_guy)),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            UserProfileCard(account, userPoints)

            Spacer(modifier = Modifier.height(16.dp))

            ExploreNeighborhoodCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Modified PlayButton to handle navigation
            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonBg)
            ) {
                Text(
                    "Start playing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    fontFamily = FontFamily(Font(R.font.luckiest_guy))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Update the BottomButtons to pass onPrizesClick and onHistoryClick
            BottomButtons(
                onPrizesClick = onPrizesClick,
                onHistoryClick = onHistoryClick,
                onCinemasClick = onCinemasClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Log Out", color = SecondaryText)
            }
        }
    }
}

@Composable
fun UserProfileCard(account: GoogleSignInAccount, userPoints: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Image(
                painter = painterResource(id = R.drawable.photouser),
                contentDescription = "User Icon",
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = account.displayName ?: "User",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = account.email ?: "",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "$userPoints pts",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text("Madrid, 1pm", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ExploreNeighborhoodCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.photouser),
                contentDescription = "Explore Image",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Explore your neighborhood", fontSize = 16.sp)
        }
    }
}

// Update the BottomButtons to include the onPrizesClick parameter and History button
// Update the BottomButtons composable to include onCinemasClick
@Composable
fun BottomButtons(
    onCinemasClick: () -> Unit = {},
    onPrizesClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onCinemasClick,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryText)
        ) {
            Text("Cinemas", fontSize = 14.sp, color = Color.Black)
        }

        Button(
            onClick = onPrizesClick,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryText)
        ) {
            Text("Prizes", fontSize = 14.sp, color = Color.Black)
        }

        Button(
            onClick = onHistoryClick,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryText)
        ) {
            Text("History", fontSize = 14.sp, color = Color.Black)
        }
    }
}


// ADDING NEAREST CINEMAS INTERACTION

// Data class for Cinema items
data class Cinema(
    val id: String,
    val name: String,
    val address: String,
    val distance: Float, // in meters
    val rating: Double
)


fun MainActivity.updateMainScreenToIncludeCinemas() {
    // Add this code inside the setContent block in MainActivity
    // Update the MainScreen call to include onCinemasClick
    // Example: MainScreen(account = currentAccount!!, userPoints = userPoints, onLogout = { signOut() },
    //          onPlayClick = { currentScreen.value = "questions" },
    //          onPrizesClick = { currentScreen.value = "prizes" },
    //          onCinemasClick = { currentScreen.value = "cinemas" })

    // Then add the condition to display NearestCinemasScreen
    // if (currentScreen.value == "cinemas") {
    //    NearestCinemasScreen(onBack = { currentScreen.value = "main" })
    // }
}



// Create a NearestCinemas Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearestCinemasScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State for location permission
    var hasLocationPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    )}

    // State for cinema list
    var cinemas by remember { mutableStateOf<List<Cinema>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Location client
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Places client
    val placesClient = remember {
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_api_key))
        }
        Places.createClient(context)
    }

    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            // Get nearby cinemas when permission is granted
            coroutineScope.launch {
                isLoading = true
                errorMessage = null
                try {
                    cinemas = getNearestCinemas(fusedLocationClient, placesClient)
                } catch (e: Exception) {
                    errorMessage = "Failed to load cinemas: ${e.message}"
                    Log.e("NearestCinemas", "Error loading cinemas", e)
                } finally {
                    isLoading = false
                }
            }
        } else {
            errorMessage = "Location permission is required to find nearby cinemas"
        }
    }

    // Effect to request permission or load cinemas on initial composition
    LaunchedEffect(key1 = Unit) {
        if (hasLocationPermission) {
            // Get nearby cinemas
            isLoading = true
            errorMessage = null
            try {
                cinemas = getNearestCinemas(fusedLocationClient, placesClient)
            } catch (e: Exception) {
                errorMessage = "Failed to load cinemas: ${e.message}"
                Log.e("NearestCinemas", "Error loading cinemas", e)
            } finally {
                isLoading = false
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nearby Cinemas",
                        color = PrimaryText,
                        fontFamily = FontFamily(Font(R.font.luckiest_guy))
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.backarrow),
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentRed
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Unknown error occurred",
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                } else {
                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            cinemas = getNearestCinemas(fusedLocationClient, placesClient)
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to load cinemas: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonBg)
                        ) {
                            Text(if (!hasLocationPermission) "Grant Permission" else "Try Again")
                        }
                    }
                }
                cinemas.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cinemas found nearby",
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(cinemas) { cinema ->
                            CinemaItem(cinema = cinema)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CinemaItem(cinema: Cinema) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle click (e.g. navigate to details) */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cinema icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ButtonBg),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.backarrow),
                    contentDescription = "Cinema",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Cinema details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = cinema.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = cinema.address,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.star), // Replace with star icon
                        contentDescription = "Rating",
                        modifier = Modifier.size(16.dp),
                        tint = AccentRed
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = String.format("%.1f", cinema.rating),
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = formatDistance(cinema.distance),
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

// Helper function to format distance
fun formatDistance(meters: Float): String {
    return when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> String.format("%.1fkm", meters / 1000)
    }
}

// Function to get nearest cinemas
@SuppressLint("MissingPermission")
suspend fun getNearestCinemas(
    fusedLocationClient: FusedLocationProviderClient,
    placesClient: PlacesClient
): List<Cinema> = withContext(Dispatchers.IO) {
    // Get current location
    val location = fusedLocationClient.lastLocation.await()
        ?: throw Exception("Unable to determine your location")

    // Use Places API to find nearby cinemas
    val placeFields = listOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG,
        Place.Field.RATING,
        Place.Field.TYPES
    )

    // Create Places request
    val request = FindCurrentPlaceRequest.newInstance(placeFields)

    try {
        // Execute the request
        val response = withContext(Dispatchers.IO) {
            placesClient.findCurrentPlace(request).await()
        }

        // Filter places that are cinemas
        // Filter places that are cinemas
        val cinemaPlaces = response.placeLikelihoods.filter { placeLikelihood ->
            val place = placeLikelihood.place
            place.types?.contains(Place.Type.MOVIE_THEATER) == true
        }

        // Convert to Cinema data class and calculate distance
        cinemaPlaces.map { placeLikelihood ->
            val place = placeLikelihood.place
            val placeLocation = Location("").apply {
                latitude = place.latLng?.latitude ?: 0.0
                longitude = place.latLng?.longitude ?: 0.0
            }

            // Calculate distance between current location and place
            val distanceInMeters = location.distanceTo(placeLocation)

            Cinema(
                id = place.id ?: "",
                name = place.name ?: "Unknown Cinema",
                address = place.address ?: "No address available",
                distance = distanceInMeters,
                rating = place.rating ?: 0.0
            )
        }.sortedBy { it.distance }
    } catch (e: Exception) {
        Log.e("NearestCinemas", "Error finding places", e)
        throw e
    }
}

// Alternative implementation using nearby search if findCurrentPlace doesn't work well
@SuppressLint("MissingPermission")
suspend fun searchNearbyCinemas(
    fusedLocationClient: FusedLocationProviderClient,
    placesClient: PlacesClient
): List<Cinema> = withContext(Dispatchers.IO) {
    try {
        // Get current location
        val location = fusedLocationClient.lastLocation.await()
            ?: throw Exception("Unable to determine your location")

        // Create a NearbySearchRequest
        val currentLatLng = LatLng(location.latitude, location.longitude)

        // We'll use a rectangular bounds around the current location
        val bounds = RectangularBounds.newInstance(
            LatLng(location.latitude - 0.02, location.longitude - 0.02),
            LatLng(location.latitude + 0.02, location.longitude + 0.02)
        )

        // For this implementation, we'd need to use Google Places API Web Service
        // through a custom API call, as the Android SDK doesn't directly support nearby search

        // This would typically be done with a Retrofit service or similar HTTP client
        // For now, we'll return a sample list to demonstrate the UI

        listOf(
            Cinema(
                id = "1",
                name = "Cineplex Madrid",
                address = "123 Gran Vía, Madrid",
                distance = 450f,
                rating = 4.5
            ),
            Cinema(
                id = "2",
                name = "Cinema City",
                address = "45 Calle Alcalá, Madrid",
                distance = 780f,
                rating = 4.2
            ),
            Cinema(
                id = "3",
                name = "Yelmo Cines",
                address = "78 Plaza España, Madrid",
                distance = 1200f,
                rating = 4.0
            ),
            Cinema(
                id = "4",
                name = "Ideal Cinema",
                address = "22 Calle Doctor Cortezo, Madrid",
                distance = 2500f,
                rating = 4.7
            )
        )
    } catch (e: Exception) {
        Log.e("NearestCinemas", "Error finding nearby cinemas", e)
        throw e
    }
}


