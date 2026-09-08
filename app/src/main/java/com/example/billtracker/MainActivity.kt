package com.example.billtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.billtracker.ui.BillListScreen
import com.example.billtracker.ui.theme.BillTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BillTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BillListScreen()
                }
            }
        }
    }
}
