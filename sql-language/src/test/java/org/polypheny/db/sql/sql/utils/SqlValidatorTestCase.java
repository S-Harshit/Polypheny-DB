/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.sql.sql.utils;


import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.nio.charset.Charset;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.polypheny.db.algebra.constant.ConformanceEnum;
import org.polypheny.db.algebra.constant.Monotonicity;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.MockCatalogReaderExtended;
import org.polypheny.db.languages.NodeParseException;
import org.polypheny.db.sql.sql.SqlNode;
import org.polypheny.db.sql.sql.SqlTestFactory;
import org.polypheny.db.sql.sql.parser.SqlParserUtil;
import org.polypheny.db.sql.sql.validate.SqlValidator;
import org.polypheny.db.util.Collation.Coercibility;
import org.polypheny.db.util.Conformance;


/**
 * An abstract base class for implementing tests against {@link SqlValidator}.
 *
 * A derived class can refine this test in two ways. First, it can add <code>testXxx()</code> methods, to test more functionality.
 *
 * Second, it can override the {@link #getTester} method to return a different implementation of the {@link Tester} object. This encapsulates the differences between test environments, for example, which SQL parser or validator to use.
 */
public class SqlValidatorTestCase {

    private static final SqlTestFactory EXTENDED_TEST_FACTORY = SqlTestFactory.INSTANCE.withCatalogReader( MockCatalogReaderExtended::new );
    static final SqlTester EXTENDED_CATALOG_TESTER = new SqlValidatorTester( EXTENDED_TEST_FACTORY );
    static final SqlTester EXTENDED_CATALOG_TESTER_2003 = new SqlValidatorTester( EXTENDED_TEST_FACTORY ).withConformance( ConformanceEnum.PRAGMATIC_2003 );
    static final SqlTester EXTENDED_CATALOG_TESTER_LENIENT = new SqlValidatorTester( EXTENDED_TEST_FACTORY ).withConformance( ConformanceEnum.LENIENT );
    public static final MethodRule TESTER_CONFIGURATION_RULE = new TesterConfigurationRule();

    protected SqlTester tester;


    /**
     * Creates a test case.
     */
    public SqlValidatorTestCase() {
        this.tester = getTester();
    }


    /**
     * Returns a tester. Derived classes should override this method to run the same set of tests in a different testing environment.
     */
    public SqlTester getTester() {
        return new SqlValidatorTester( SqlTestFactory.INSTANCE );
    }


    public final Sql sql( String sql ) {
        return new Sql( tester, sql, true );
    }


    public final Sql expr( String sql ) {
        return new Sql( tester, sql, false );
    }


    public final Sql winSql( String sql ) {
        return sql( sql );
    }


    public final Sql win( String sql ) {
        return sql( "select * from emp " + sql );
    }


    public Sql winExp( String sql ) {
        return winSql( "select " + sql + " from emp window w as (order by deptno)" );
    }


    public Sql winExp2( String sql ) {
        return winSql( "select " + sql + " from emp" );
    }


    public void check( String sql ) {
        sql( sql ).ok();
    }


    public void checkExp( String sql ) {
        tester.assertExceptionIsThrown( AbstractSqlTester.buildQuery( sql ), null );
    }


    /**
     * Checks that a SQL query gives a particular error, or succeeds if {@code expected} is null.
     */
    public final void checkFails( String sql, String expected ) {
        sql( sql ).fails( expected );
    }


    /**
     * Checks that a SQL expression gives a particular error.
     */
    public final void checkExpFails( String sql, String expected ) {
        tester.assertExceptionIsThrown( AbstractSqlTester.buildQuery( sql ), expected );
    }


    /**
     * Checks that a SQL expression gives a particular error, and that the location of the error is the whole expression.
     */
    public final void checkWholeExpFails( String sql, String expected ) {
        assert sql.indexOf( '^' ) < 0;
        checkExpFails( "^" + sql + "^", expected );
    }


    public final void checkExpType( String sql, String expected ) {
        checkColumnType( AbstractSqlTester.buildQuery( sql ), expected );
    }


    /**
     * Checks that a query returns a single column, and that the column has the expected type. For example,
     *
     * <code>checkColumnType("SELECT empno FROM Emp", "INTEGER NOT NULL");</code>
     *
     * @param sql Query
     * @param expected Expected type, including nullability
     */
    public final void checkColumnType( String sql, String expected ) {
        tester.checkColumnType( sql, expected );
    }


    /**
     * Checks that a query returns a row of the expected type. For example,
     *
     * <code>checkResultType("select empno, name from emp","{EMPNO INTEGER NOT NULL, NAME VARCHAR(10) NOT NULL}");</code>
     *
     * @param sql Query
     * @param expected Expected row type
     */
    public final void checkResultType( String sql, String expected ) {
        tester.checkResultType( sql, expected );
    }


    /**
     * Checks that the first column returned by a query has the expected type. For example,
     *
     * <code>checkQueryType("SELECT empno FROM Emp", "INTEGER NOT NULL");</code>
     *
     * @param sql Query
     * @param expected Expected type, including nullability
     */
    public final void checkIntervalConv( String sql, String expected ) {
        tester.checkIntervalConv( AbstractSqlTester.buildQuery( sql ), expected );
    }


    protected final void assertExceptionIsThrown( String sql, String expectedMsgPattern ) {
        assert expectedMsgPattern != null;
        tester.assertExceptionIsThrown( sql, expectedMsgPattern );
    }


    public void checkCharset( String sql, Charset expectedCharset ) {
        tester.checkCharset( sql, expectedCharset );
    }


    public void checkCollation( String sql, String expectedCollationName, Coercibility expectedCoercibility ) {
        tester.checkCollation( sql, expectedCollationName, expectedCoercibility );
    }


    /**
     * Checks whether an exception matches the expected pattern. If <code>sap</code> contains an error location, checks this too.
     *
     * @param ex Exception thrown
     * @param expectedMsgPattern Expected pattern
     * @param sap Query and (optional) position in query
     */
    public static void checkEx( Throwable ex, String expectedMsgPattern, SqlParserUtil.StringAndPos sap ) {
        SqlTests.checkEx( ex, expectedMsgPattern, sap, SqlTests.Stage.VALIDATE );
    }


    /**
     * Encapsulates differences between test environments, for example, which SQL parser or validator to use.
     *
     * It contains a mock schema with <code>EMP</code> and <code>DEPT</code> tables, which can run without having to start up Farrago.
     */
    public interface Tester {

        SqlNode parseQuery( String sql ) throws NodeParseException;

        SqlNode parseAndValidate( SqlValidator validator, String sql );

        SqlValidator getValidator();

        /**
         * Checks that a query is valid, or, if invalid, throws the right message at the right location.
         *
         * If <code>expectedMsgPattern</code> is null, the query must succeed.
         *
         * If <code>expectedMsgPattern</code> is not null, the query must fail, and give an error location of (expectedLine, expectedColumn) through (expectedEndLine, expectedEndColumn).
         *
         * @param sql SQL statement
         * @param expectedMsgPattern If this parameter is null the query must be valid for the test to pass; If this parameter is not null the query must be malformed and the message given must match the pattern
         */
        void assertExceptionIsThrown( String sql, String expectedMsgPattern );

        /**
         * Returns the data type of the sole column of a SQL query.
         *
         * For example, <code>getResultType("VALUES (1")</code> returns <code>INTEGER</code>.
         *
         * Fails if query returns more than one column.
         *
         * @see #getResultType(String)
         */
        AlgDataType getColumnType( String sql );

        /**
         * Returns the data type of the row returned by a SQL query.
         *
         * For example, <code>getResultType("VALUES (1, 'foo')")</code> returns <code>RecordType(INTEGER EXPR$0, CHAR(3) EXPR#1)</code>.
         */
        AlgDataType getResultType( String sql );

        void checkCollation( String sql, String expectedCollationName, Coercibility expectedCoercibility );

        void checkCharset( String sql, Charset expectedCharset );

        /**
         * Checks that a query returns one column of an expected type. For example, <code>checkType("VALUES (1 + 2)", "INTEGER NOT NULL")</code>.
         */
        void checkColumnType( String sql, String expected );

        /**
         * Given a SQL query, returns a list of the origins of each result field.
         *
         * @param sql SQL query
         * @param fieldOriginList Field origin list, e.g. "{(CATALOG.SALES.EMP.EMPNO, null)}"
         */
        void checkFieldOrigin( String sql, String fieldOriginList );

        /**
         * Checks that a query gets rewritten to an expected form.
         *
         * @param validator validator to use; null for default
         * @param query query to test
         * @param expectedRewrite expected SQL text after rewrite and unparse
         */
        void checkRewrite( SqlValidator validator, String query, String expectedRewrite );

        /**
         * Checks that a query returns one column of an expected type. For example, <code>checkType("select empno, name from emp""{EMPNO INTEGER NOT NULL, NAME VARCHAR(10) NOT NULL}")</code>.
         */
        void checkResultType( String sql, String expected );

        /**
         * Checks if the interval value conversion to milliseconds is valid. For example, <code>checkIntervalConv(VALUES (INTERVAL '1' Minute), "60000")</code>.
         */
        void checkIntervalConv( String sql, String expected );

        /**
         * Given a SQL query, returns the monotonicity of the first item in the SELECT clause.
         *
         * @param sql SQL query
         * @return Monotonicity
         */
        Monotonicity getMonotonicity( String sql );

        Conformance getConformance();

    }


    /**
     * Fluent testing API.
     */
    static class Sql {

        private final SqlTester tester;
        private final String sql;


        /**
         * Creates a Sql.
         *
         * @param tester Tester
         * @param sql SQL query or expression
         * @param query True if {@code sql} is a query, false if it is an expression
         */
        Sql( SqlTester tester, String sql, boolean query ) {
            this.tester = tester;
            this.sql = query ? sql : AbstractSqlTester.buildQuery( sql );
        }


        Sql tester( SqlTester tester ) {
            return new Sql( tester, sql, true );
        }


        public Sql sql( String sql ) {
            return new Sql( tester, sql, true );
        }


        Sql withExtendedCatalog() {
            return tester( EXTENDED_CATALOG_TESTER );
        }


        Sql withExtendedCatalog2003() {
            return tester( EXTENDED_CATALOG_TESTER_2003 );
        }


        Sql withExtendedCatalogLenient() {
            return tester( EXTENDED_CATALOG_TESTER_LENIENT );
        }


        Sql ok() {
            tester.assertExceptionIsThrown( sql, null );
            return this;
        }


        Sql fails( String expected ) {
            tester.assertExceptionIsThrown( sql, expected );
            return this;
        }


        Sql failsIf( boolean b, String expected ) {
            if ( b ) {
                fails( expected );
            } else {
                ok();
            }
            return this;
        }


        public Sql type( String expectedType ) {
            tester.checkResultType( sql, expectedType );
            return this;
        }


        public Sql columnType( String expectedType ) {
            tester.checkColumnType( sql, expectedType );
            return this;
        }


        public Sql monotonic( Monotonicity expectedMonotonicity ) {
            tester.checkMonotonic( sql, expectedMonotonicity );
            return this;
        }


        public Sql bindType( final String bindType ) {
            tester.check( sql, null, parameterRowType -> assertThat( parameterRowType.toString(), is( bindType ) ), result -> {
            } );
            return this;
        }


        /**
         * Removes the carets from the SQL string. Useful if you want to run a test once at a conformance level where it fails, then run it again at a conformance level where it succeeds.
         */
        public Sql sansCarets() {
            return new Sql( tester, sql.replace( "^", "" ), true );
        }

    }


    /**
     * Enables to configure {@link #tester} behavior on a per-test basis. {@code tester} object is created in the test object constructor, and there's no trivial way to override its features.
     * This JUnit rule enables post-process test object on a per test method basis
     */
    private static class TesterConfigurationRule implements MethodRule {

        @Override
        public Statement apply( Statement statement, FrameworkMethod frameworkMethod, Object o ) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    SqlValidatorTestCase tc = (SqlValidatorTestCase) o;
                    SqlTester tester = tc.tester;
                    WithLex lex = frameworkMethod.getAnnotation( WithLex.class );
                    if ( lex != null ) {
                        tester = tester.withLex( lex.value() );
                    }
                    tc.tester = tester;
                    statement.evaluate();
                }
            };
        }

    }

}
