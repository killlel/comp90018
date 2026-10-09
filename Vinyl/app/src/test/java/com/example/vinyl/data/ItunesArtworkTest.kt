package com.example.vinyl.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ItunesArtworkTest {

    @Test
    fun `swaps the size segment of an iTunes cover`() {
        val small = "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/ab/cd/ef/abcdef.jpg/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/ab/cd/ef/abcdef.jpg/600x600bb.jpg",
            itunesArtworkAt(small, 600),
        )
    }

    @Test
    fun `keeps the image format`() {
        assertEquals("https://x.mzstatic.com/a/600x600bb.png", itunesArtworkAt("https://x.mzstatic.com/a/100x100bb.png", 600))
    }

    @Test
    fun `leaves any other url alone`() {
        val other = "https://example.com/covers/album.jpg"
        assertEquals(other, itunesArtworkAt(other, 600))
    }
}
