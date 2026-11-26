package com.example.scratched

data class NearbyPerson(val profile: NearbyProfile, val address: String)

class NearbyPeople(private val selfId: Int) {
    private data class Seen(val person: NearbyPerson, val time: Long)
    private val seen = mutableMapOf<Int, Seen>()

    fun observe(profile: NearbyProfile, address: String, time: Long) {
        if (profile.id == selfId || (seen[profile.id]?.time ?: Long.MIN_VALUE) > time) return
        seen[profile.id] = Seen(NearbyPerson(profile, address), time)
    }

    fun present(now: Long): List<NearbyPerson> {
        seen.entries.removeAll { now - it.value.time >= 20_000 }
        return seen.values.map { it.person }.sortedWith(compareBy({ it.profile.name }, { it.profile.id }))
    }

    fun clear() = seen.clear()
}
