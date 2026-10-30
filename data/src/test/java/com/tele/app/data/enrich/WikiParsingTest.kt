package com.tele.app.data.enrich

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiParsingTest {

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun `solo los ids de imdb valen y sin sufijo de episodio`() {
        assertEquals("tt0903747", WikiParser.imdbId("tt0903747:1:1"))
        assertEquals("tt0133093", WikiParser.imdbId("tt0133093"))
        assertNull(WikiParser.imdbId("kitsu:123"))
        assertNull(WikiParser.imdbId(null))
        assertNull(WikiParser.imdbId("tt123"))
    }

    @Test
    fun `del buscador sale el qid de la entidad`() {
        val root = json("""{"query":{"search":[{"title":"Q1079","pageid":1424}]}}""")
        assertEquals("Q1079", WikiParser.searchEntityId(root))
    }

    @Test
    fun `un resultado que no es qid se descarta`() {
        val root = json("""{"query":{"search":[{"title":"Breaking Bad"}]}}""")
        assertNull(WikiParser.searchEntityId(root))
    }

    @Test
    fun `una entidad inexistente no se confunde con datos`() {
        val root = json("""{"entities":{"Q1":{"missing":""}}}""")
        assertNull(WikiParser.entity(root, "Q1"))
    }

    @Test
    fun `una serie se queda con el creador y no con los directores de episodios`() {
        val facts = WikiParser.facts(entity(), "series")

        assertEquals(listOf("Q4500"), facts.crewIds)
        assertEquals(listOf("Q271050", "Q302491"), facts.castIds)
        assertEquals(listOf("Q652390"), facts.studioIds)
        assertEquals(listOf("Q30"), facts.countryIds)
        assertEquals(listOf("Q7976"), facts.languageIds)
        assertEquals(2008, facts.year)
        assertEquals("Breaking Bad", facts.eswikiTitle)
    }

    @Test
    fun `una pelicula usa el director`() {
        val facts = WikiParser.facts(entity(), "movie")
        assertEquals(listOf("Q999999"), facts.crewIds)
    }

    @Test
    fun `las etiquetas prefieren espanol y guardan el articulo`() {
        val labels = WikiParser.labels(
            json(
                """
                {"entities":{
                  "Q4500":{"labels":{"es":{"value":"Vince Gilligan"}},
                           "sitelinks":{"eswiki":{"title":"Vince Gilligan"}}},
                  "Q652390":{"labels":{"en":{"value":"Sony Pictures Television"}}}
                }}
                """.trimIndent()
            )
        )

        assertEquals("Vince Gilligan", labels["Q4500"]?.text)
        assertEquals("es", labels["Q4500"]?.wikiLang)
        assertEquals("Vince Gilligan", labels["Q4500"]?.wikiTitle)

        assertEquals("Sony Pictures Television", labels["Q652390"]?.text)
        assertNull(labels["Q652390"]?.wikiTitle)
    }

    @Test
    fun `un resumen de desambiguacion no se usa`() {
        val root = json("""{"type":"disambiguation","extract":"The Matrix puede referirse a"}""")
        assertNull(WikiParser.summary(root))
    }

    @Test
    fun `el resumen trae texto limpio y su url`() {
        val root = json(
            """
            {"type":"standard",
             "extract":"<b>Breaking Bad</b> es una serie de televisión estadounidense &amp; multipremiada.",
             "content_urls":{"desktop":{"page":"https://es.wikipedia.org/wiki/Breaking_Bad"}}}
            """.trimIndent()
        )

        val summary = WikiParser.summary(root)!!
        assertEquals("Breaking Bad es una serie de televisión estadounidense & multipremiada.", summary.extract)
        assertEquals("https://es.wikipedia.org/wiki/Breaking_Bad", summary.pageUrl)
    }

    @Test
    fun `el texto se limpia y se corta`() {
        assertEquals("Hola & adiós mundo", WikiText.clean("  <i>Hola</i> &amp;   adiós\nmundo  "))
        assertNull(WikiText.clean("   "))
        assertNull(WikiText.clean(null))

        val long = (1..60).joinToString(" ") { "palabra$it" }
        val cut = WikiText.clean(long, maxChars = 80)!!
        assertEquals(true, cut.endsWith("…"))
        assertEquals(true, cut.length <= 81)
    }

    private fun entity() = WikiParser.entity(entityRoot(), "Q1079")!!

    private fun entityRoot() = json(
        """
        {"entities":{"Q1079":{
          "claims":{
            "P170":[{"mainsnak":{"datavalue":{"value":{"id":"Q4500"}}}}],
            "P57":[{"mainsnak":{"datavalue":{"value":{"id":"Q999999"}}}}],
            "P161":[
              {"mainsnak":{"datavalue":{"value":{"id":"Q271050"}}}},
              {"mainsnak":{"datavalue":{"value":{"id":"Q302491"}}}}
            ],
            "P272":[{"mainsnak":{"datavalue":{"value":{"id":"Q652390"}}}}],
            "P495":[{"mainsnak":{"datavalue":{"value":{"id":"Q30"}}}}],
            "P364":[{"mainsnak":{"datavalue":{"value":{"id":"Q7976"}}}}],
            "P580":[{"mainsnak":{"datavalue":{"value":{"time":"+2008-01-20T00:00:00Z","precision":9}}}}]
          },
          "sitelinks":{"eswiki":{"title":"Breaking Bad"}}
        }}}
        """.trimIndent()
    )
}
