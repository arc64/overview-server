package com.overviewdocs.postgres

import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.dsl.ast.QueryExpressionElements
import org.squeryl.internals.{DBType,FieldMetaData,FieldStatementParam,StatementWriter,Utils}
import org.squeryl.Schema

class SquerylPostgreSqlAdapter extends PostgreSqlAdapter {
  override def createSequenceName(fmd: FieldMetaData) = {
    fmd.parentMetaData.viewOrTable.name + "_" + fmd.columnName + "_seq"
  }

  // Handles Postgres ENUM types properly
  // https://groups.google.com/forum/?fromgroups=#!topic/squeryl/pTXgPe8pQIs
  //
  // This is WRONG: use Enumeration instead!
  override protected def writeValue(o: AnyRef, fmd: FieldMetaData, sw: StatementWriter): String = {
    if (sw.isForDisplay) {
      super.writeValue(o, fmd, sw)
    } else {
      sw.addParam(FieldStatementParam(o, fmd))

      val v = fmd.getNativeJdbcValue(o)
      if (fmd.isCustomType && v.isInstanceOf[PostgresqlEnum]) {
        // FIXME remove PostgresqlEnum (and this branch) in favor of Enumeration
        "? ::" + v.asInstanceOf[PostgresqlEnum].typeName
      } else if (fmd.wrappedFieldType eq classOf[InetAddress]) {
        "? ::cidr"
      } else {
        "?"
      }
    }
  }
}
