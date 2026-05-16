package dev.milinko.workoutapp.filters

class EMA(private val alpha: Float) {
    private var value: Double? = null
    fun update(v: Float): Double {
        value = if (value == null)
            v.toDouble()
        else
            alpha * v + (1 - alpha) * value!!
        return value!!
    }
    fun update(v: Double): Double {
        value = if (value == null)
            v
        else
            alpha * v + (1 - alpha) * value!!
        return value!!
    }
    fun reset() { value = null }
    fun get() = value
}