package com.egepaksoy.ghost.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Material icons that are not in the small core icon set. */
object GIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(addPathNodes(p), fill = SolidColor(Color.Black))
        }.build()

    val Clock = icon("Clock", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z")
    val Map = icon("Map", "M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z")
    val Download = icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    val Battery = icon("Battery", "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4z")
    val Bars = icon("Bars", "M4,12h3.5v8H4zM10.25,5h3.5v15h-3.5zM16.5,9H20v11h-3.5z")
    val Timeline = icon("Timeline", "M23,8c0,1.1 -0.9,2 -2,2 -0.18,0 -0.35,-0.02 -0.51,-0.07l-3.56,3.55c0.05,0.16 0.07,0.34 0.07,0.52 0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2c0,-0.18 0.02,-0.36 0.07,-0.52l-2.55,-2.55c-0.16,0.05 -0.34,0.07 -0.52,0.07s-0.36,-0.02 -0.52,-0.07l-4.55,4.56c0.05,0.16 0.07,0.33 0.07,0.51 0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2 0.9,-2 2,-2c0.18,0 0.35,0.02 0.51,0.07l4.56,-4.55C8.02,9.36 8,9.18 8,9c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,0.18 -0.02,0.36 -0.07,0.52l2.55,2.55c0.16,-0.05 0.34,-0.07 0.52,-0.07s0.36,0.02 0.52,0.07l3.55,-3.56C19.02,8.35 19,8.18 19,8c0,-1.1 0.9,-2 2,-2s2,0.9 2,2z")
    val Route = icon("Route", "M19,15.18V7c0,-2.21 -1.79,-4 -4,-4s-4,1.79 -4,4v10c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2V8.82C8.16,8.4 9,7.3 9,6c0,-1.66 -1.34,-3 -3,-3S3,4.34 3,6c0,1.3 0.84,2.4 2,2.82V17c0,2.21 1.79,4 4,4s4,-1.79 4,-4V7c0,-1.1 0.9,-2 2,-2s2,0.9 2,2v8.18c-1.16,0.41 -2,1.51 -2,2.82 0,1.66 1.34,3 3,3s3,-1.34 3,-3c0,-1.3 -0.84,-2.4 -2,-2.82z")
}
