package com.example.scratched

import org.junit.Assert.*
import org.junit.Test

class NearbyPeopleTest {
    @Test fun threePhonesSeeTwoDistinctPeopleAndRotationUpdatesTheAddress() {
        val users = (1..3).map { NearbyProfile(it, "Саша") }
        users.forEach { me ->
            val list = NearbyPeople(me.id)
            users.forEach { list.observe(it, "address-${it.id}", 0) }
            val other = users.first { it.id != me.id }
            list.observe(other, "rotated", 10_000)
            list.observe(other, "old", 100)
            assertEquals(2, list.present(10_000).size)
            assertEquals(NearbyPerson(other, "rotated"), list.present(20_000).single())
            assertTrue(list.present(30_000).isEmpty())
        }
    }

    @Test fun namesFitInALegacyAdvertisementAndSurviveTheHandshake() {
        for (name in listOf("АнастасЯ", "Phoenix1", "Ёж")) {
            val profile = NearbyProfile(-123, name)
            assertEquals(profile, NearbyProfile.decode(profile.encode()))
            assertTrue(18 + profile.encode().size <= 31)
        }
        assertNull(NearbyProfile.decode(byteArrayOf(1, 0, 0, 0, 1)))
        assertNull(NearbyProfile.decode(byteArrayOf(2, 0, 0, 0, 1, 0)))
        assertFalse(NearbyProfile.validName("🙂"))
        assertFalse(NearbyProfile.validName("         "))
    }
}
