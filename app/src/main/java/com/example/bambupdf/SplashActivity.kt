package com.example.bambupdf

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF7F1D1D), // Deep Maroon
                                Color(0xFFDC2626), // Crimson Red
                                Color(0xFF1E3A8A)  // Royal Sapphire
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Menampilkan Logo Panda BambuPDF dengan Bingkai Emas Bulat
                    Image(
                        painter = painterResource(id = R.drawable.logo_bambupdf),
                        contentDescription = "Logo BambuPDF",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(165.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color(0xFFF59E0B), CircleShape)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "BambuPDF",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFFF59E0B),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White)
                    ) {
                        Text(
                            text = "CAMSCAN AI • OCR • CRYPTO-SEAL",
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    CircularProgressIndicator(
                        color = Color(0xFFF59E0B),
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Official Product by PandaKode",
                        color = Color(0xFFFEF3C7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Otomatis pindah ke MainActivity setelah 2.2 detik
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2200)
    }
}