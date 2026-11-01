package com.tele.app.data.enrich

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JustWatchTest {

    private val client = JustWatchClient()

    @Test
    fun `parsePlatforms extrae correctamente Filmin y otras plataformas en suscripcion`() {
        val sampleJson = """
        {
          "data": {
            "popularTitles": {
              "edges": [
                {
                  "node": {
                    "id": "ts123",
                    "content": {
                      "title": "Drive My Car",
                      "externalIds": {
                        "imdbId": "tt14039582"
                      }
                    },
                    "offers": [
                      {
                        "monetizationType": "FLATRATE",
                        "package": {
                          "clearName": "Filmin Plus",
                          "shortName": "fil"
                        }
                      },
                      {
                        "monetizationType": "FREE",
                        "package": {
                          "clearName": "rtve",
                          "shortName": "rtv"
                        }
                      },
                      {
                        "monetizationType": "RENT",
                        "package": {
                          "clearName": "Rakuten TV",
                          "shortName": "wki"
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val platforms = client.parsePlatforms(sampleJson, "tt14039582")
        assertEquals(listOf("Filmin", "RTVE Play"), platforms)
    }

    @Test
    fun `parsePlatforms devuelve lista vacia en json corrupto o sin ofertas`() {
        assertTrue(client.parsePlatforms("{}", null).isEmpty())
        assertTrue(client.parsePlatforms("no json", null).isEmpty())
    }
}
