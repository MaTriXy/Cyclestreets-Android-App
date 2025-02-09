package net.cyclestreets.routing

import java.util.LinkedList

import net.cyclestreets.util.Turn
import net.cyclestreets.util.Turn.*
import org.osmdroid.api.IGeoPoint

class Segments : Iterable<Segment> {
    private val segments = LinkedList<Segment>()

    fun count(): Int { return segments.size }
    fun isEmpty(): Boolean { return segments.isEmpty() }

    fun startPoint(): IGeoPoint { return segments.first.start() }
    fun finishPoint(): IGeoPoint { return segments.last.finish() }
    fun first(): Segment.Start { return segments.first as Segment.Start }
    fun last(): Segment.End { return segments.last as Segment.End }

    private val CROSSROAD_MELDS: Map<Pair<Turn, Turn>, Turn> = mapOf(
        Pair(TURN_LEFT, TURN_RIGHT) to TURN_LEFT_THEN_RIGHT,
        Pair(TURN_RIGHT, TURN_LEFT) to TURN_RIGHT_THEN_LEFT,
        Pair(BEAR_LEFT, BEAR_RIGHT) to BEAR_LEFT_THEN_RIGHT,
        Pair(BEAR_RIGHT, BEAR_LEFT) to BEAR_RIGHT_THEN_LEFT
    )

    fun add(seg: Segment) {
        if (seg is Segment.Start) {
            segments.addFirst(seg)
            return
        }

        if (count() != 0) {
            val previous = segments[count() - 1]

            // Meld "Join Roundabout" instructions
            if (Turn.JOIN_ROUNDABOUT == previous.turn) {
                segments.remove(previous)
                segments.add(Segment.Step(previous, seg, seg.turn, seg.turnInstruction))
                return
            }

            // Meld staggered crossroads
            if (previous.distance < 20) {
                CROSSROAD_MELDS[Pair(previous.turn, seg.turn)]?.let {
                    segments.remove(previous)
                    if (previous.walk == seg.walk) {
                        // Combine the 2 segments:
                        segments.add(Segment.Step(previous, seg, it, it.textInstruction))
                        return
                    }
                    else {
                    // If the 2 segments aren't both walking or both cycling, keep the segments separate.
                    // Add previous segment with combined turn instruction and CURRENT street,
                    // so user still gets warning of the double turn:
                        segments.add(Segment.Step(
                            seg.name,
                            previous.legNumber,
                            it,
                            it.textInstruction,
                            previous.walk,
                            previous.cumulativeTime,
                            previous.distance,
                            previous.cumulativeDistance,
                            previous.points
                        ))
                    }
                    // then add current seg with current details (not combined) - which is done below so no need to do it here
                }
            }

            // Meld bridges, e.g. #83784189
            if (previous.distance < 100 && "Bridge".equals(previous.name, ignoreCase = true) && Turn.STRAIGHT_ON === seg.turn) {
                segments.remove(previous)
                if (previous.walk == seg.walk) {
                    // Combine the 2 segments:
                    segments.add(Segment.Step(previous, seg, previous.turn, previous.turnInstruction + " over Bridge"))
                    return
                }
                else {
                    // If the 2 segments aren't both walking or both cycling, keep the segments separate.
                    // Add previous segment with combined turn instruction and CURRENT street,
                    // so user still gets warning of the double instruction:
                    segments.add(Segment.Step(
                        seg.name,
                        previous.legNumber,
                        previous.turn,
                        previous.turnInstruction + " over Bridge",
                        previous.walk,
                        previous.cumulativeTime,
                        previous.distance,
                        previous.cumulativeDistance,
                        previous.points
                    ))
                }
                // then add current seg with current details (not combined) - which is done below so no need to do it here
            }
        }

        segments.add(seg)
    }

    operator fun get(i: Int): Segment { return segments[i] }

    override fun iterator(): Iterator<Segment> { return segments.iterator() }
    fun pointsIterator(): Iterator<IGeoPoint> { return PointsIterator(this) }

    private class PointsIterator internal constructor(segments: Segments) : Iterator<IGeoPoint> {
        private val segmentIterator: Iterator<Segment> = segments.iterator()
        private var pointIterator: Iterator<IGeoPoint>? = null

        init {
            if (segmentIterator.hasNext())
                pointIterator = segmentIterator.next().points()
        }

        override fun hasNext(): Boolean {
            return pointIterator?.hasNext() ?: false
        }

        override fun next(): IGeoPoint {
            if (!hasNext())
                throw IllegalStateException()

            val p = pointIterator!!.next()

            if (!hasNext()) {
                if (segmentIterator.hasNext())
                    pointIterator = segmentIterator.next().points()
                else
                    pointIterator = null
            }

            return p
        }
    }
}
