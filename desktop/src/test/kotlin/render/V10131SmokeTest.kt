package render
import com.combo.monkeycompanion.engine.show.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
class V10131SmokeTest {
    @Test fun smooth() {
        for (s in 1..40) { val r = ChaseRun.random(Random(s.toLong()), EndKind.entries[s % 4], 2.1f)
            for (role in Role.entries) { val a = ChaseRun.Pos(); val b = ChaseRun.Pos(); r.frame(role, 0f, a); var t = 0f
                while (t < r.total) { t += 1f/60f; r.frame(role, t, b); val j = Math.hypot((b.x-a.x).toDouble(), (b.y-a.y).toDouble())
                    var da = Math.abs(b.ang - a.ang) % 360f; if (da > 180f) da = 360f - da
                    assertTrue("seed $s $role jump $j t=$t", j < 0.03); assertTrue("seed $s $role spin $da t=$t leg=${r.legAt(b.dist).jump}", da < 15f)
                    a.x=b.x; a.y=b.y; a.ang=b.ang } } } }
}
