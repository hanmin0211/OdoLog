package odolog.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CheckTest {
    @Test
    fun carFileOnlyAcceptsPlates() {
        val dir = File("x")
        assertEquals("12가3456.csv", carFile(dir, "12가3456")?.name)
        assertNotNull(carFile(dir, "서울123가4567"))
        assertNull(carFile(dir, "..\\..\\etc"))
        assertNull(carFile(dir, "12가3456/../a"))
        assertNull(carFile(dir, null))
    }

    @Test
    fun tripsFormat() {
        assertNull(checkTrips("2026-10-21,08:12:00,08:47:00,48182.2,48196.0,13.8,,출퇴근,영업팀,강지민\n"))
        assertNull(checkTrips("2026-10-21,08:12:00,08:47:00,1.0,2.0,1.0,,\n"))  // 예전 8칸 기록
        assertNull(checkTrips(""))
        assertNotNull(checkTrips("2026-10-21,08:12,08:47,x,2.0,1.0,,업무"))
        assertNotNull(checkTrips("hello"))
    }

    @Test
    fun rosterFormat() {
        assertNull(checkRoster("강지민\t영업팀\n김철수\n"))
        assertNotNull(checkRoster("강지민,영업팀"))
        assertNotNull(checkRoster("\t영업팀"))
        assertNotNull(checkRoster(" \n"))
    }
}
