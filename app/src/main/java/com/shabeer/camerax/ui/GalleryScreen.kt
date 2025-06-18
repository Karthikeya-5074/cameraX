package com.shabeer.camerax.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.io.File

@Composable
fun GalleryScreen(navController: NavController) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val mediaDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            .resolve("SmartCameraX")
    }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    val files = remember(hasPermission) {
        if (hasPermission) mediaDir.listFiles()?.sortedByDescending { it.lastModified() }
            ?: emptyArray() else emptyArray()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp).clickable { selectedFile = file }
                ) {
                    if (file.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(120.dp)
                                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                        }
                    }
                    Text(file.name, color = Color.White, fontSize = 12.sp)
                }
            }
        }
        IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        selectedFile?.let { file ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { selectedFile = null },
                contentAlignment = Alignment.Center
            ) {
                if (file.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                    AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    AndroidView(factory = {
                        VideoView(it).apply {
                            setVideoURI(Uri.fromFile(file))
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
