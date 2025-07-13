import io.ybigta.text2sql.ingest.RelationType
import io.ybigta.text2sql.ingest.TblRelation
import org.junit.Test
import kotlin.test.assertEquals

class TblRelationTest {
    @Test
    fun parseTest() {
        assertEquals(
            TblRelation(
                fromTbl = TblRelation.Column("wic", "qm_imp_msrs_pic", "imp_msrs_pic_id"),
                toTbl = TblRelation.Column("comn", "cm_user", "user_id"),
                relationType = RelationType.MANY_TO_ONE
            ),
            TblRelation.parseDBML("wic.qm_imp_msrs_pic.imp_msrs_pic_id > comn.cm_user.user_id").getOrThrow()
        )
    }

    @Test
    fun `relation symbol error test`() {
        TblRelation.parseDBML("wic.qm_imp_msrs_pic.imp_msrs_pic_id = comn.cm_user.user_id")
            .onSuccess { throw IllegalStateException("expected to fail") }

        TblRelation.parseDBML("wic.qm_imp_msrs_pic.imp_msrs_pic_id <=> comn.cm_user.user_id")
            .onSuccess { throw IllegalStateException("expected to fail") }

    }
}