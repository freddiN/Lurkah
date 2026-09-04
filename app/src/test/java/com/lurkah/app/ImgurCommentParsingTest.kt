package com.lurkah.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgurCommentParsingTest {

    private val gson = Gson()

    @Test
    fun testParseNestedComments_hierarchyPreserved() {
        val json = """
            {
              "data": [
                {
                  "id": 1, "image_id": "abc", "comment": "Top comment",
                  "author": "user1", "ups": 10, "downs": 1, "points": 9.0,
                  "datetime": 1700000000, "parent_id": 0, "deleted": false,
                  "children": [
                    {
                      "id": 2, "image_id": "abc", "comment": "Reply",
                      "author": "user2", "ups": 5, "downs": 0, "points": 5.0,
                      "datetime": 1700000100, "parent_id": 1, "deleted": false,
                      "children": []
                    }
                  ]
                }
              ],
              "success": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, ImgurCommentsResponse::class.java)

        assertTrue(response.success)
        assertEquals(1, response.data.size)
        assertEquals("Top comment", response.data[0].comment)
        assertEquals(1, response.data[0].children?.size)
        assertEquals("Reply", response.data[0].children?.get(0)?.comment)
        assertEquals("user2", response.data[0].children?.get(0)?.author)
    }

    @Test
    fun testParseDeeplyNested_threeLevels() {
        val json = """
            {
              "data": [
                {
                  "id": 10, "image_id": "x", "comment": "L0",
                  "author": "a", "ups": 1, "downs": 0, "points": 1.0,
                  "datetime": 1, "parent_id": 0, "deleted": false,
                  "children": [
                    {
                      "id": 11, "image_id": "x", "comment": "L1",
                      "author": "b", "ups": 1, "downs": 0, "points": 1.0,
                      "datetime": 2, "parent_id": 10, "deleted": false,
                      "children": [
                        {
                          "id": 12, "image_id": "x", "comment": "L2",
                          "author": "c", "ups": 2, "downs": 0, "points": 2.0,
                          "datetime": 3, "parent_id": 11, "deleted": false,
                          "children": []
                        }
                      ]
                    }
                  ]
                }
              ],
              "success": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, ImgurCommentsResponse::class.java)
        val l2 = response.data[0].children?.get(0)?.children?.get(0)

        assertEquals("L2", l2?.comment)
        assertEquals(java.lang.Long.valueOf(11L), l2?.parentId)
    }

    @Test
    fun testParseEmptyComments() {
        val response = gson.fromJson(
            """{"data": [], "success": true}""",
            ImgurCommentsResponse::class.java
        )

        assertTrue(response.success)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun testParseMissingOptionalFields_nullSafe() {
        val json = """
            {
              "data": [{"id": 99, "comment": "minimal"}],
              "success": true
            }
        """.trimIndent()

        val response = gson.fromJson(json, ImgurCommentsResponse::class.java)

        assertEquals(1, response.data.size)
        assertEquals("minimal", response.data[0].comment)
        assertNull(response.data[0].author)
        assertNull(response.data[0].children)
    }

    @Test
    fun testPointsFallback_upsUsedWhenPointsNull() {
        // UI zeigt points?.toInt() ?: ups — hier nur Modell-Check:
        // points kann null sein, ups trägt den Score
        val comment = ImgurComment(
            id = 5, imageId = "x", comment = "c", author = "a",
            ups = 7, downs = 0, points = null, datetime = null,
            parentId = null, deleted = false, children = emptyList()
        )

        val displayedPoints = comment.points?.toInt() ?: (comment.ups ?: 0)
        assertEquals(7, displayedPoints)
    }
}
