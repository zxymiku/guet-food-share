package com.guet.foodshare

import org.json.JSONObject

/** A round is exhausted before any ID is offered again. Nothing is persisted. */
class DecisionDeck {
    data class Draw(val places:List<JSONObject>,val remaining:Int,val total:Int)
    private var signature=emptySet<String>()
    private var remaining=mutableListOf<String>()
    private var previous=emptySet<String>()

    fun draw(places:List<JSONObject>):Draw {
        val pool=places.associateBy{it.getString("id")}
        if(signature!=pool.keys){signature=pool.keys.toSet();remaining.clear();previous=emptySet()}
        if(remaining.isEmpty()){
            val shuffled=pool.keys.shuffled()
            remaining=(shuffled.filter{it !in previous}+shuffled.filter{it in previous}).toMutableList()
        }
        val ids=remaining.take(3)
        remaining=remaining.drop(ids.size).toMutableList()
        previous=ids.toSet()
        return Draw(ids.mapNotNull{pool[it]},remaining.size,pool.size)
    }
}
