/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.sql.core;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.calcite.avatica.util.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.metadata.NullSentinel;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.languages.OperatorRegistry;
import org.polypheny.db.plan.AlgOptPredicateList;
import org.polypheny.db.plan.AlgOptUtil;
import org.polypheny.db.plan.Strong;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexInputRef;
import org.polypheny.db.rex.RexInterpreter;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexLocalRef;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexProgram;
import org.polypheny.db.rex.RexProgramBuilder;
import org.polypheny.db.rex.RexSimplify;
import org.polypheny.db.rex.RexUnknownAs;
import org.polypheny.db.rex.RexUtil;
import org.polypheny.db.sql.sql.SqlOperator;
import org.polypheny.db.sql.sql.SqlSpecialOperator;
import org.polypheny.db.test.PolyphenyDbAssert;
import org.polypheny.db.test.RexProgramBuilderBase;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeAssignmentRules;
import org.polypheny.db.type.inference.ReturnTypes;
import org.polypheny.db.util.DateString;
import org.polypheny.db.util.ImmutableBitSet;
import org.polypheny.db.util.NlsString;
import org.polypheny.db.util.TestUtil;
import org.polypheny.db.util.TimeString;
import org.polypheny.db.util.TimestampString;
import org.polypheny.db.util.TimestampWithTimeZoneString;
import org.polypheny.db.util.Util;


/**
 * Unit tests for {@link RexProgram} and {@link RexProgramBuilder}.
 */
public class RexProgramTest extends RexProgramBuilderBase {

    /**
     * Creates a RexProgramTest.
     */
    public RexProgramTest() {
        super();
    }


    @Override
    @Before
    public void setUp() {
        super.setUp();
    }


    private void checkCnf( RexNode node, String expected ) {
        assertThat( "RexUtil.toCnf(rexBuilder, " + node + ")", RexUtil.toCnf( rexBuilder, node ).toString(), equalTo( expected ) );
    }


    private void checkThresholdCnf( RexNode node, int threshold, String expected ) {
        assertThat( "RexUtil.toCnf(rexBuilder, threshold=" + threshold + " , " + node + ")", RexUtil.toCnf( rexBuilder, threshold, node ).toString(), equalTo( expected ) );
    }


    private void checkPullFactorsUnchanged( RexNode node ) {
        checkPullFactors( node, node.toString() );
    }


    private void checkPullFactors( RexNode node, String expected ) {
        assertThat( "RexUtil.pullFactors(rexBuilder, " + node + ")", RexUtil.pullFactors( rexBuilder, node ).toString(), equalTo( expected ) );
    }


    /**
     * Asserts that given node has expected string representation with account of node type
     *
     * @param message extra message that clarifies where the node came from
     * @param expected expected string representation of the node
     * @param node node to check
     */
    private void assertNode( String message, String expected, RexNode node ) {
        String actual;
        if ( node.isA( Kind.CAST ) || node.isA( Kind.NEW_SPECIFICATION ) ) {
            // toString contains type (see RexCall.toString)
            actual = node.toString();
        } else {
            actual = node + ":" + node.getType() + (node.getType().isNullable() ? "" : " NOT NULL");
        }
        assertEquals( message, expected, actual );
    }


    /**
     * Simplifies an expression and checks that the result is as expected.
     */
    private void checkSimplify( RexNode node, String expected ) {
        final String nodeString = node.toString();
        checkSimplify3_( node, expected, expected, expected );
        if ( expected.equals( nodeString ) ) {
            throw new AssertionError( "expected == node.toString(); use checkSimplifyUnchanged" );
        }
    }


    /**
     * Simplifies an expression and checks that the result is unchanged.
     */
    private void checkSimplifyUnchanged( RexNode node ) {
        final String expected = node.toString();
        checkSimplify3_( node, expected, expected, expected );
    }


    /**
     * Simplifies an expression and checks the result if unknowns remain unknown, or if unknown becomes false. If the result is the same, use {@link #checkSimplify(RexNode, String)}.
     *
     * @param node Expression to simplify
     * @param expected Expected simplification
     * @param expectedFalse Expected simplification, if unknown is to be treated as false
     */
    private void checkSimplify2( RexNode node, String expected, String expectedFalse ) {
        checkSimplify3_( node, expected, expectedFalse, expected );
        if ( expected.equals( expectedFalse ) ) {
            throw new AssertionError( "expected == expectedFalse; use checkSimplify" );
        }
    }


    private void checkSimplify3( RexNode node, String expected, String expectedFalse, String expectedTrue ) {
        checkSimplify3_( node, expected, expectedFalse, expectedTrue );
        if ( expected.equals( expectedFalse ) && expected.equals( expectedTrue ) ) {
            throw new AssertionError( "expected == expectedFalse == expectedTrue; use checkSimplify" );
        }
        if ( expected.equals( expectedTrue ) ) {
            throw new AssertionError( "expected == expectedTrue; use checkSimplify2" );
        }
    }


    private void checkSimplify3_( RexNode node, String expected, String expectedFalse, String expectedTrue ) {
        final RexNode simplified = simplify.simplifyUnknownAs( node, RexUnknownAs.UNKNOWN );
        assertThat( "simplify(unknown as unknown): " + node, simplified.toString(), equalTo( expected ) );
        if ( node.getType().getPolyType() == PolyType.BOOLEAN ) {
            final RexNode simplified2 = simplify.simplifyUnknownAs( node, RexUnknownAs.FALSE );
            assertThat( "simplify(unknown as false): " + node, simplified2.toString(), equalTo( expectedFalse ) );
            final RexNode simplified3 = simplify.simplifyUnknownAs( node, RexUnknownAs.TRUE );
            assertThat( "simplify(unknown as true): " + node, simplified3.toString(), equalTo( expectedTrue ) );
        } else {
            assertThat( "node type is not BOOLEAN, so <<expectedFalse>> should match <<expected>>", expectedFalse, is( expected ) );
            assertThat( "node type is not BOOLEAN, so <<expectedTrue>> should match <<expected>>", expectedTrue, is( expected ) );
        }
    }


    private void checkSimplifyFilter( RexNode node, String expected ) {
        final RexNode simplified = this.simplify.simplifyUnknownAs( node, RexUnknownAs.FALSE );
        assertThat( simplified.toString(), equalTo( expected ) );
    }


    private void checkSimplifyFilter( RexNode node, AlgOptPredicateList predicates, String expected ) {
        final RexNode simplified = simplify.withPredicates( predicates ).simplifyUnknownAs( node, RexUnknownAs.FALSE );
        assertThat( simplified.toString(), equalTo( expected ) );
    }


    /**
     * Returns the number of nodes (including leaves) in a Rex tree.
     */
    private static int nodeCount( RexNode node ) {
        int n = 1;
        if ( node instanceof RexCall ) {
            for ( RexNode operand : ((RexCall) node).getOperands() ) {
                n += nodeCount( operand );
            }
        }
        return n;
    }


    /**
     * Tests construction of a RexProgram.
     */
    @Test
    public void testBuildProgram() {
        final RexProgramBuilder builder = createProg( 0 );
        final RexProgram program = builder.getProgram( false );
        final String programString = program.toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($0, 1)], expr#3=[77], expr#4=[+($0, $1)], expr#5=[+($0, $0)], expr#6=[+($t4, $t2)], a=[$t6], b=[$t5])", programString );

        // Normalize the program using the RexProgramBuilder.normalize API. Note that unused expression '77' is eliminated, input refs (e.g. $0) become local refs (e.g. $t0), and constants are assigned to locals.
        final RexProgram normalizedProgram = program.normalize( rexBuilder, null );
        final String normalizedProgramString = normalizedProgram.toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t0)], a=[$t5], b=[$t6])", normalizedProgramString );
    }


    /**
     * Tests construction and normalization of a RexProgram.
     */
    @Test
    public void testNormalize() {
        final RexProgramBuilder builder = createProg( 0 );
        final String program = builder.getProgram( true ).toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t0)], a=[$t5], b=[$t6])", program );
    }


    /**
     * Tests construction and normalization of a RexProgram.
     */
    @Test
    public void testElimDups() {
        final RexProgramBuilder builder = createProg( 1 );
        final String unnormalizedProgram = builder.getProgram( false ).toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($0, 1)], expr#3=[77], expr#4=[+($0, $1)], expr#5=[+($0, 1)], expr#6=[+($0, $t5)], expr#7=[+($t4, $t2)], a=[$t7], b=[$t6])", unnormalizedProgram );

        // normalize eliminates duplicates (specifically "+($0, $1)")
        final RexProgramBuilder builder2 = createProg( 1 );
        final String program2 = builder2.getProgram( true ).toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t4)], a=[$t5], b=[$t6])", program2 );
    }


    /**
     * Tests how the condition is simplified.
     */
    @Test
    public void testSimplifyCondition() {
        final RexProgram program = createProg( 3 ).getProgram( false );
        assertThat(
                program.toString(),
                is( "(expr#0..1=[{inputs}], expr#2=[+($0, 1)], expr#3=[77], expr#4=[+($0, $1)], expr#5=[+($0, 1)], expr#6=[+($0, $t5)], expr#7=[+($t4, $t2)], expr#8=[5], expr#9=[>($t2, $t8)], "
                        + "expr#10=[true], expr#11=[IS NOT NULL($t5)], expr#12=[false], expr#13=[null:BOOLEAN], expr#14=[CASE($t9, $t10, $t11, $t12, $t13)], expr#15=[NOT($t14)], a=[$t7], b=[$t6], $condition=[$t15])" ) );

        assertThat(
                program.normalize( rexBuilder, simplify ).toString(),
                is( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t4)], expr#7=[5], expr#8=[>($t4, $t7)], expr#9=[NOT($t8)], a=[$t5], b=[$t6], $condition=[$t9])" ) );
    }


    /**
     * Tests how the condition is simplified.
     */
    @Test
    public void testSimplifyCondition2() {
        final RexProgram program = createProg( 4 ).getProgram( false );
        assertThat(
                program.toString(),
                is( "(expr#0..1=[{inputs}], expr#2=[+($0, 1)], expr#3=[77], expr#4=[+($0, $1)], expr#5=[+($0, 1)], expr#6=[+($0, $t5)], expr#7=[+($t4, $t2)], expr#8=[5], expr#9=[>($t2, $t8)], expr#10=[true], expr#11=[IS NOT NULL($t5)], expr#12=[false], "
                        + "expr#13=[null:BOOLEAN], expr#14=[CASE($t9, $t10, $t11, $t12, $t13)], expr#15=[NOT($t14)], expr#16=[IS TRUE($t15)], a=[$t7], b=[$t6], $condition=[$t16])" ) );

        assertThat(
                program.normalize( rexBuilder, simplify ).toString(),
                is( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t4)], expr#7=[5], expr#8=[>($t4, $t7)], expr#9=[NOT($t8)], a=[$t5], b=[$t6], $condition=[$t9])" ) );
    }


    /**
     * Checks translation of AND(x, x).
     */
    @Test
    public void testDuplicateAnd() {
        // RexProgramBuilder used to translate AND(x, x) to x. Now it translates it to AND(x, x).
        // The optimization of AND(x, x) => x occurs at a higher level.
        final RexProgramBuilder builder = createProg( 2 );
        final String program = builder.getProgram( true ).toString();
        TestUtil.assertEqualsVerbose( "(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[1], expr#4=[+($t0, $t3)], expr#5=[+($t2, $t4)], expr#6=[+($t0, $t0)], expr#7=[>($t2, $t0)], a=[$t5], b=[$t6], $condition=[$t7])", program );
    }


    /**
     * Creates a program, depending on variant:
     *
     * <ol>
     * <li><code>select (x + y) + (x + 1) as a, (x + x) as b from t(x, y)</code>
     * <li><code>select (x + y) + (x + 1) as a, (x + (x + 1)) as b from t(x, y)</code>
     * <li><code>select (x + y) + (x + 1) as a, (x + x) as b from t(x, y) where ((x + y) &gt; 1) and ((x + y) &gt; 1)</code>
     * <li><code>select (x + y) + (x + 1) as a, (x + x) as b from t(x, y) where not case when x + 1 &gt; 5 then true when y is null then null else false end</code>
     * </ol>
     */
    private RexProgramBuilder createProg( int variant ) {
        assert variant >= 0 && variant <= 4;
        List<AlgDataType> types =
                Arrays.asList(
                        typeFactory.createPolyType( PolyType.INTEGER ),
                        typeFactory.createPolyType( PolyType.INTEGER ) );
        List<String> names = Arrays.asList( "x", "y" );
        AlgDataType inputRowType = typeFactory.createStructType( types, names );
        final RexProgramBuilder builder = new RexProgramBuilder( inputRowType, rexBuilder );
        // $t0 = x
        // $t1 = y
        // $t2 = $t0 + 1 (i.e. x + 1)
        final RexNode i0 = rexBuilder.makeInputRef( types.get( 0 ), 0 );
        final RexLiteral c1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral c5 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 5L ) );
        RexLocalRef t2 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), i0, c1 ) );
        // $t3 = 77 (not used)
        final RexLiteral c77 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 77 ) );
        RexLocalRef t3 = builder.addExpr( c77 );
        Util.discard( t3 );
        // $t4 = $t0 + $t1 (i.e. x + y)
        final RexNode i1 = rexBuilder.makeInputRef( types.get( 1 ), 1 );
        RexLocalRef t4 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), i0, i1 ) );
        RexLocalRef t5;
        final RexLocalRef t1;
        switch ( variant ) {
            case 0:
            case 2:
                // $t5 = $t0 + $t0 (i.e. x + x)
                t5 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), i0, i0 ) );
                t1 = null;
                break;
            case 1:
            case 3:
            case 4:
                // $tx = $t0 + 1
                t1 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), i0, c1 ) );
                // $t5 = $t0 + $tx (i.e. x + (x + 1))
                t5 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), i0, t1 ) );
                break;
            default:
                throw new AssertionError( "unexpected variant " + variant );
        }
        // $t6 = $t4 + $t2 (i.e. (x + y) + (x + 1))
        RexLocalRef t6 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.PLUS ), t4, t2 ) );
        builder.addProject( t6.getIndex(), "a" );
        builder.addProject( t5.getIndex(), "b" );

        final RexLocalRef t7;
        final RexLocalRef t8;
        switch ( variant ) {
            case 2:
                // $t7 = $t4 > $i0 (i.e. (x + y) > 0)
                t7 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.GREATER_THAN ), t4, i0 ) );
                // $t8 = $t7 AND $t7
                t8 = builder.addExpr( and( t7, t7 ) );
                builder.addCondition( t8 );
                builder.addCondition( t7 );
                break;
            case 3:
            case 4:
                // $t7 = 5
                t7 = builder.addExpr( c5 );
                // $t8 = $t2 > $t7 (i.e. (x + 1) > 5)
                t8 = builder.addExpr( gt( t2, t7 ) );
                // $t9 = true
                final RexLocalRef t9 = builder.addExpr( trueLiteral );
                // $t10 = $t1 is not null (i.e. y is not null)
                assert t1 != null;
                final RexLocalRef t10 = builder.addExpr( rexBuilder.makeCall( OperatorRegistry.get( OperatorName.IS_NOT_NULL ), t1 ) );
                // $t11 = false
                final RexLocalRef t11 = builder.addExpr( falseLiteral );
                // $t12 = unknown
                final RexLocalRef t12 = builder.addExpr( nullBool );
                // $t13 = case when $t8 then $t9 when $t10 then $t11 else $t12 end
                final RexLocalRef t13 = builder.addExpr( case_( t8, t9, t10, t11, t12 ) );
                // $t14 = not $t13 (i.e. not case ... end)
                final RexLocalRef t14 = builder.addExpr( not( t13 ) );
                // don't add 't14 is true' - that is implicit
                if ( variant == 3 ) {
                    builder.addCondition( t14 );
                } else {
                    // $t15 = $14 is true
                    final RexLocalRef t15 = builder.addExpr( isTrue( t14 ) );
                    builder.addCondition( t15 );
                }
        }
        return builder;
    }


    /**
     * Unit test for {@link Strong}.
     */
    @Test
    public void testStrong() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );

        final ImmutableBitSet c = ImmutableBitSet.of();
        final ImmutableBitSet c0 = ImmutableBitSet.of( 0 );
        final ImmutableBitSet c1 = ImmutableBitSet.of( 1 );
        final ImmutableBitSet c01 = ImmutableBitSet.of( 0, 1 );
        final ImmutableBitSet c13 = ImmutableBitSet.of( 1, 3 );

        // input ref
        final RexInputRef i0 = rexBuilder.makeInputRef( intType, 0 );
        final RexInputRef i1 = rexBuilder.makeInputRef( intType, 1 );

        assertThat( Strong.isNull( i0, c0 ), is( true ) );
        assertThat( Strong.isNull( i0, c1 ), is( false ) );
        assertThat( Strong.isNull( i0, c01 ), is( true ) );
        assertThat( Strong.isNull( i0, c13 ), is( false ) );

        // literals are strong iff they are always null
        assertThat( Strong.isNull( trueLiteral, c ), is( false ) );
        assertThat( Strong.isNull( trueLiteral, c13 ), is( false ) );
        assertThat( Strong.isNull( falseLiteral, c13 ), is( false ) );
        assertThat( Strong.isNull( nullInt, c ), is( true ) );
        assertThat( Strong.isNull( nullInt, c13 ), is( true ) );
        assertThat( Strong.isNull( nullBool, c13 ), is( true ) );

        // AND is strong if one of its arguments is strong
        final RexNode andUnknownTrue = and( nullBool, trueLiteral );
        final RexNode andTrueUnknown = and( trueLiteral, nullBool );
        final RexNode andFalseTrue = and( falseLiteral, trueLiteral );

        assertThat( Strong.isNull( andUnknownTrue, c ), is( false ) );
        assertThat( Strong.isNull( andTrueUnknown, c ), is( false ) );
        assertThat( Strong.isNull( andFalseTrue, c ), is( false ) );

        // If i0 is null, "i0 and i1 is null" is null
        assertThat( Strong.isNull( and( i0, isNull( i1 ) ), c0 ), is( false ) );
        // If i1 is null, "i0 and i1" is false
        assertThat( Strong.isNull( and( i0, isNull( i1 ) ), c1 ), is( false ) );
        // If i0 and i1 are both null, "i0 and i1" is null
        assertThat( Strong.isNull( and( i0, i1 ), c01 ), is( true ) );
        assertThat( Strong.isNull( and( i0, i1 ), c1 ), is( false ) );
        // If i0 and i1 are both null, "i0 and isNull(i1) is false"
        assertThat( Strong.isNull( and( i0, isNull( i1 ) ), c01 ), is( false ) );
        // If i0 and i1 are both null, "i0 or i1" is null
        assertThat( Strong.isNull( or( i0, i1 ), c01 ), is( true ) );
        // If i0 is null, "i0 or i1" is not necessarily null
        assertThat( Strong.isNull( or( i0, i1 ), c0 ), is( false ) );
        assertThat( Strong.isNull( or( i0, i1 ), c1 ), is( false ) );

        // If i0 is null, then "i0 is not null" is false
        RexNode i0NotNull = isNotNull( i0 );
        assertThat( Strong.isNull( i0NotNull, c0 ), is( false ) );
        assertThat( Strong.isNotTrue( i0NotNull, c0 ), is( true ) );

        // If i0 is null, then "not(i0 is not null)" is true.
        // Join-strengthening relies on this.
        RexNode notI0NotNull = not( isNotNull( i0 ) );
        assertThat( Strong.isNull( notI0NotNull, c0 ), is( false ) );
        assertThat( Strong.isNotTrue( notI0NotNull, c0 ), is( false ) );

        // NULLIF(null, null): null
        // NULLIF(null, X): null
        // NULLIF(X, X/Y): null or X
        // NULLIF(X, null): X
        assertThat( Strong.isNull( nullIf( nullInt, nullInt ), c ), is( true ) );
        assertThat( Strong.isNull( nullIf( nullInt, trueLiteral ), c ), is( true ) );
        assertThat( Strong.isNull( nullIf( trueLiteral, trueLiteral ), c ), is( false ) );
        assertThat( Strong.isNull( nullIf( trueLiteral, falseLiteral ), c ), is( false ) );
        assertThat( Strong.isNull( nullIf( trueLiteral, nullInt ), c ), is( false ) );

        // ISNULL(null) is true, ISNULL(not null value) is false
        assertThat( Strong.isNull( isNull( nullInt ), c01 ), is( false ) );
        assertThat( Strong.isNull( isNull( trueLiteral ), c01 ), is( false ) );

        // CASE ( <predicate1> <value1> <predicate2> <value2> <predicate3> <value3> ...)
        // only definitely null if all values are null.
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), nullInt, ge( i0, i1 ), nullInt, nullInt ), c01 ),
                is( true ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), i0, ge( i0, i1 ), nullInt, nullInt ), c01 ),
                is( true ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), i0, ge( i0, i1 ), nullInt, nullInt ), c1 ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), nullInt, ge( i0, i1 ), i0, nullInt ), c01 ),
                is( true ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), nullInt, ge( i0, i1 ), i0, nullInt ), c1 ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), nullInt, ge( i0, i1 ), nullInt, i0 ), c01 ),
                is( true ) );
        assertThat(
                Strong.isNull( case_( eq( i0, i1 ), nullInt, ge( i0, i1 ), nullInt, i0 ), c1 ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( isNotNull( i0 ), i0, i1 ), c ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( isNotNull( i0 ), i0, i1 ), c0 ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( isNotNull( i0 ), i0, i1 ), c1 ),
                is( false ) );
        assertThat(
                Strong.isNull( case_( isNotNull( i0 ), i0, i1 ), c01 ),
                is( true ) );

    }


    @Test
    public void xAndNotX() {
        checkSimplify2(
                and( vBool(), not( vBool() ), vBool( 1 ), not( vBool( 1 ) ) ),
                "AND(null, IS NULL(?0:ROW.bool0), IS NULL(?0:ROW.bool1))",
                "false" );

        checkSimplify2(
                and( vBool(), vBool( 1 ), not( vBool( 1 ) ) ),
                "AND(?0:ROW.bool0, null, IS NULL(?0:ROW.bool1))",
                "false" );

        checkSimplify(
                and( vBool(), not( vBool() ), vBoolNotNull( 1 ), not( vBoolNotNull( 1 ) ) ),
                "false" );
    }


    /**
     * Unit test for {@link RexUtil#isLosslessCast(RexNode)}.
     */
    @Test
    public void testLosslessCast() {
        final AlgDataType tinyIntType = typeFactory.createPolyType( PolyType.TINYINT );
        final AlgDataType smallIntType = typeFactory.createPolyType( PolyType.SMALLINT );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType bigIntType = typeFactory.createPolyType( PolyType.BIGINT );
        final AlgDataType floatType = typeFactory.createPolyType( PolyType.FLOAT );
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType charType5 = typeFactory.createPolyType( PolyType.CHAR, 5 );
        final AlgDataType charType6 = typeFactory.createPolyType( PolyType.CHAR, 6 );
        final AlgDataType varCharType10 = typeFactory.createPolyType( PolyType.VARCHAR, 10 );
        final AlgDataType varCharType11 = typeFactory.createPolyType( PolyType.VARCHAR, 11 );

        // Negative
        assertThat( RexUtil.isLosslessCast( rexBuilder.makeInputRef( intType, 0 ) ), is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( tinyIntType, rexBuilder.makeInputRef( smallIntType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( smallIntType, rexBuilder.makeInputRef( intType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( intType, rexBuilder.makeInputRef( bigIntType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( bigIntType, rexBuilder.makeInputRef( floatType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( booleanType, rexBuilder.makeInputRef( bigIntType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( intType, rexBuilder.makeInputRef( charType5, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( intType, rexBuilder.makeInputRef( varCharType10, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType10, rexBuilder.makeInputRef( varCharType11, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( charType5, rexBuilder.makeInputRef( bigIntType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( charType5, rexBuilder.makeInputRef( smallIntType, 0 ) ) ),
                is( false ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType10, rexBuilder.makeInputRef( intType, 0 ) ) ),
                is( false ) );

        // Positive
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( smallIntType, rexBuilder.makeInputRef( tinyIntType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( intType, rexBuilder.makeInputRef( smallIntType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( bigIntType, rexBuilder.makeInputRef( intType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( intType, rexBuilder.makeInputRef( intType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( charType6, rexBuilder.makeInputRef( smallIntType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType10, rexBuilder.makeInputRef( smallIntType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType11, rexBuilder.makeInputRef( intType, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType11, rexBuilder.makeInputRef( charType6, 0 ) ) ),
                is( true ) );
        assertThat(
                RexUtil.isLosslessCast( rexBuilder.makeCast( varCharType11, rexBuilder.makeInputRef( varCharType10, 0 ) ) ),
                is( true ) );
    }


    @Test
    public void removeRedundantCast() {
        checkSimplify( cast( vInt(), nullable( tInt() ) ), "?0:ROW.int0" );
        checkSimplifyUnchanged( cast( vInt(), tInt() ) );
        checkSimplify( cast( vIntNotNull(), nullable( tInt() ) ), "?0:ROW.notNullInt0" );
        checkSimplify( cast( vIntNotNull(), tInt() ), "?0:ROW.notNullInt0" );

        // Nested int int cast is removed
        checkSimplify( cast( cast( vVarchar(), tInt() ), tInt() ), "CAST(?0:ROW.varchar0):INTEGER NOT NULL" );
        checkSimplifyUnchanged( cast( cast( vVarchar(), tInt() ), tVarchar() ) );
    }


    @Test
    public void testNoCommonReturnTypeFails() {
        try {
            final RexNode node = coalesce( vVarchar( 1 ), vInt( 2 ) );
            fail( "expected exception, got " + node );
        } catch ( IllegalArgumentException e ) {
            final String expected = "Cannot infer return type for COALESCE; operand types: [VARCHAR, INTEGER]";
            assertThat( e.getMessage(), is( expected ) );
        }
    }


    /**
     * Unit test for {@link RexUtil#toCnf}.
     */
    @Test
    public void testCnf() {
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, booleanType )
                .add( "b", null, booleanType )
                .add( "c", null, booleanType )
                .add( "d", null, booleanType )
                .add( "e", null, booleanType )
                .add( "f", null, booleanType )
                .add( "g", null, booleanType )
                .add( "h", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode dRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode eRef = rexBuilder.makeFieldAccess( range, 4 );
        final RexNode fRef = rexBuilder.makeFieldAccess( range, 5 );
        final RexNode gRef = rexBuilder.makeFieldAccess( range, 6 );
        final RexNode hRef = rexBuilder.makeFieldAccess( range, 7 );

        final RexLiteral sevenLiteral = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 7 ) );
        final RexNode hEqSeven = eq( hRef, sevenLiteral );

        checkCnf( aRef, "?0:ROW.a" );
        checkCnf( trueLiteral, "true" );
        checkCnf( falseLiteral, "false" );
        checkCnf( nullBool, "null:BOOLEAN" );
        checkCnf( and( aRef, bRef ), "AND(?0:ROW.a, ?0:ROW.b)" );
        checkCnf( and( aRef, bRef, cRef ), "AND(?0:ROW.a, ?0:ROW.b, ?0:ROW.c)" );

        checkCnf( and( or( aRef, bRef ), or( cRef, dRef ) ), "AND(OR(?0:ROW.a, ?0:ROW.b), OR(?0:ROW.c, ?0:ROW.d))" );
        checkCnf( or( and( aRef, bRef ), and( cRef, dRef ) ), "AND(OR(?0:ROW.a, ?0:ROW.c), OR(?0:ROW.a, ?0:ROW.d), OR(?0:ROW.b, ?0:ROW.c), OR(?0:ROW.b, ?0:ROW.d))" );
        // Input has nested ORs, output ORs are flat
        checkCnf( or( and( aRef, bRef ), or( cRef, dRef ) ), "AND(OR(?0:ROW.a, ?0:ROW.c, ?0:ROW.d), OR(?0:ROW.b, ?0:ROW.c, ?0:ROW.d))" );

        checkCnf( or( aRef, not( and( bRef, not( hEqSeven ) ) ) ), "OR(?0:ROW.a, NOT(?0:ROW.b), =(?0:ROW.h, 7))" );

        // apply de Morgan's theorem
        checkCnf( not( or( aRef, not( bRef ) ) ), "AND(NOT(?0:ROW.a), ?0:ROW.b)" );

        // apply de Morgan's theorem,
        // filter out 'OR ... FALSE' and 'AND ... TRUE'
        checkCnf( not( or( and( aRef, trueLiteral ), not( bRef ), falseLiteral ) ), "AND(NOT(?0:ROW.a), ?0:ROW.b)" );

        checkCnf( and( aRef, or( bRef, and( cRef, dRef ) ) ), "AND(?0:ROW.a, OR(?0:ROW.b, ?0:ROW.c), OR(?0:ROW.b, ?0:ROW.d))" );

        checkCnf(
                and( aRef, or( bRef, and( cRef, or( dRef, and( eRef, or( fRef, gRef ) ) ) ) ) ),
                "AND(?0:ROW.a, OR(?0:ROW.b, ?0:ROW.c), OR(?0:ROW.b, ?0:ROW.d, ?0:ROW.e), OR(?0:ROW.b, ?0:ROW.d, ?0:ROW.f, ?0:ROW.g))" );

        checkCnf(
                and( aRef, or( bRef, and( cRef, or( dRef, and( eRef, or( fRef, and( gRef, or( trueLiteral, falseLiteral ) ) ) ) ) ) ) ),
                "AND(?0:ROW.a, OR(?0:ROW.b, ?0:ROW.c), OR(?0:ROW.b, ?0:ROW.d, ?0:ROW.e), OR(?0:ROW.b, ?0:ROW.d, ?0:ROW.f, ?0:ROW.g))" );
    }


    /**
     * Unit test for "Add RexUtil.toCnf, to convert expressions to conjunctive normal form (CNF)".
     */
    @Test
    public void testCnf2() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "x", null, intType )
                .add( "y", null, intType )
                .add( "z", null, intType )
                .add( "a", null, intType )
                .add( "b", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode xRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode yRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode zRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 4 );

        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 1 ) );
        final RexLiteral literal2 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 2 ) );
        final RexLiteral literal3 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 3 ) );

        checkCnf(
                or(
                        and(
                                eq( xRef, literal1 ),
                                eq( yRef, literal1 ),
                                eq( zRef, literal1 ) ),
                        and(
                                eq( xRef, literal2 ),
                                eq( yRef, literal2 ),
                                eq( aRef, literal2 ) ),
                        and(
                                eq( xRef, literal3 ),
                                eq( aRef, literal3 ),
                                eq( bRef, literal3 ) ) ),
                "AND("
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.y, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.y, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.y, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.a, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.a, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.x, 1), =(?0:ROW.a, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.x, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.x, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.x, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.y, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.y, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.y, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.a, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.a, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.y, 1), =(?0:ROW.a, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.x, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.x, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.x, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.y, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.y, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.y, 2), =(?0:ROW.b, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.a, 2), =(?0:ROW.x, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.a, 2), =(?0:ROW.a, 3)), "
                        + "OR(=(?0:ROW.z, 1), =(?0:ROW.a, 2), =(?0:ROW.b, 3)))" );
    }


    /**
     * Unit test for "When converting to CNF, fail if the expression exceeds a threshold".
     */
    @Test
    public void testThresholdCnf() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "x", null, intType )
                .add( "y", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode xRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode yRef = rexBuilder.makeFieldAccess( range, 1 );

        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 1 ) );
        final RexLiteral literal2 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 2 ) );
        final RexLiteral literal3 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 3 ) );
        final RexLiteral literal4 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 4 ) );

        // Expression
        //   OR(=(?0:ROW.x, 1), AND(=(?0:ROW.x, 2), =(?0:ROW.y, 3)))
        // transformation creates 7 nodes
        //   AND(OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2)), OR(=(?0:ROW.x, 1), =(?0:ROW.y, 3)))
        // Thus, it is triggered.
        checkThresholdCnf(
                or( eq( xRef, literal1 ), and( eq( xRef, literal2 ), eq( yRef, literal3 ) ) ),
                8, "AND(OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2)), OR(=(?0:ROW.x, 1), =(?0:ROW.y, 3)))" );

        // Expression
        //   OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), AND(=(?0:ROW.x, 3), =(?0:ROW.y, 4)))
        // transformation creates 9 nodes
        //   AND(OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), =(?0:ROW.x, 3)),
        //       OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), =(?0:ROW.y, 8)))
        // Thus, it is NOT triggered.
        checkThresholdCnf(
                or( eq( xRef, literal1 ), eq( xRef, literal2 ), and( eq( xRef, literal3 ), eq( yRef, literal4 ) ) ),
                8, "OR(=(?0:ROW.x, 1), =(?0:ROW.x, 2), AND(=(?0:ROW.x, 3), =(?0:ROW.y, 4)))" );
    }


    /**
     * Tests formulas of various sizes whose size is exponential when converted to CNF.
     */
    @Test
    public void testCnfExponential() {
        // run out of memory if limit is higher than about 20
        final int limit = PolyphenyDbAssert.ENABLE_SLOW ? 16 : 6;
        for ( int i = 2; i < limit; i++ ) {
            checkExponentialCnf( i );
        }
    }


    private void checkExponentialCnf( int n ) {
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataTypeFactory.Builder builder = typeFactory.builder();
        for ( int i = 0; i < n; i++ ) {
            builder.add( "x" + i, null, booleanType )
                    .add( "y" + i, null, booleanType );
        }
        final AlgDataType rowType3 = builder.build();
        final RexDynamicParam range3 = rexBuilder.makeDynamicParam( rowType3, 0 );
        final List<RexNode> list = new ArrayList<>();
        for ( int i = 0; i < n; i++ ) {
            list.add( and( rexBuilder.makeFieldAccess( range3, i * 2 ), rexBuilder.makeFieldAccess( range3, i * 2 + 1 ) ) );
        }
        final RexNode cnf = RexUtil.toCnf( rexBuilder, or( list ) );
        final int nodeCount = nodeCount( cnf );
        assertThat( (n + 1) * (int) Math.pow( 2, n ) + 1, equalTo( nodeCount ) );
        if ( n == 3 ) {
            assertThat(
                    cnf.toString(),
                    equalTo( "AND(OR(?0:ROW.x0, ?0:ROW.x1, ?0:ROW.x2), OR(?0:ROW.x0, ?0:ROW.x1, ?0:ROW.y2), OR(?0:ROW.x0, ?0:ROW.y1, ?0:ROW.x2), OR(?0:ROW.x0, ?0:ROW.y1, ?0:ROW.y2), OR(?0:ROW.y0, ?0:ROW.x1, ?0:ROW.x2), OR(?0:ROW.y0, ?0:ROW.x1, ?0:ROW.y2), OR(?0:ROW.y0, ?0:ROW.y1, ?0:ROW.x2), OR(?0:ROW.y0, ?0:ROW.y1, ?0:ROW.y2))" ) );
        }
    }


    /**
     * Unit test for {@link RexUtil#pullFactors}.
     */
    @Test
    public void testPullFactors() {
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, booleanType )
                .add( "b", null, booleanType )
                .add( "c", null, booleanType )
                .add( "d", null, booleanType )
                .add( "e", null, booleanType )
                .add( "f", null, booleanType )
                .add( "g", null, booleanType )
                .add( "h", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode dRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode eRef = rexBuilder.makeFieldAccess( range, 4 );
        final RexNode fRef = rexBuilder.makeFieldAccess( range, 5 );
        final RexNode gRef = rexBuilder.makeFieldAccess( range, 6 );
        final RexNode hRef = rexBuilder.makeFieldAccess( range, 7 );

        final RexLiteral sevenLiteral = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 7 ) );
        final RexNode hEqSeven = eq( hRef, sevenLiteral );

        // Most of the expressions in testCnf are unaffected by pullFactors.
        checkPullFactors(
                or( and( aRef, bRef ), and( cRef, aRef, dRef, aRef ) ),
                "AND(?0:ROW.a, OR(?0:ROW.b, AND(?0:ROW.c, ?0:ROW.d)))" );

        checkPullFactors( aRef, "?0:ROW.a" );
        checkPullFactors( trueLiteral, "true" );
        checkPullFactors( falseLiteral, "false" );
        checkPullFactors( nullBool, "null:BOOLEAN" );
        checkPullFactors( and( aRef, bRef ), "AND(?0:ROW.a, ?0:ROW.b)" );
        checkPullFactors( and( aRef, bRef, cRef ), "AND(?0:ROW.a, ?0:ROW.b, ?0:ROW.c)" );

        checkPullFactorsUnchanged( and( or( aRef, bRef ), or( cRef, dRef ) ) );
        checkPullFactorsUnchanged( or( and( aRef, bRef ), and( cRef, dRef ) ) );
        // Input has nested ORs, output ORs are flat; different from CNF
        checkPullFactors( or( and( aRef, bRef ), or( cRef, dRef ) ), "OR(AND(?0:ROW.a, ?0:ROW.b), ?0:ROW.c, ?0:ROW.d)" );

        checkPullFactorsUnchanged( or( aRef, not( and( bRef, not( hEqSeven ) ) ) ) );
        checkPullFactorsUnchanged( not( or( aRef, not( bRef ) ) ) );
        checkPullFactorsUnchanged( not( or( and( aRef, trueLiteral ), not( bRef ), falseLiteral ) ) );
        checkPullFactorsUnchanged( and( aRef, or( bRef, and( cRef, dRef ) ) ) );

        checkPullFactorsUnchanged( and( aRef, or( bRef, and( cRef, or( dRef, and( eRef, or( fRef, gRef ) ) ) ) ) ) );

        checkPullFactorsUnchanged( and( aRef, or( bRef, and( cRef, or( dRef, and( eRef, or( fRef, and( gRef, or( trueLiteral, falseLiteral ) ) ) ) ) ) ) ) );
    }


    @Test
    public void testSimplify() {
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType intNullableType = typeFactory.createTypeWithNullability( intType, true );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, booleanType )
                .add( "b", null, booleanType )
                .add( "c", null, booleanType )
                .add( "d", null, booleanType )
                .add( "e", null, booleanType )
                .add( "f", null, booleanType )
                .add( "g", null, booleanType )
                .add( "h", null, intType )
                .add( "i", null, intNullableType )
                .add( "j", null, intType )
                .add( "k", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode dRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode eRef = rexBuilder.makeFieldAccess( range, 4 );
        final RexNode hRef = rexBuilder.makeFieldAccess( range, 7 );
        final RexNode iRef = rexBuilder.makeFieldAccess( range, 8 );
        final RexNode jRef = rexBuilder.makeFieldAccess( range, 9 );
        final RexNode kRef = rexBuilder.makeFieldAccess( range, 10 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );

        // and: remove duplicates
        checkSimplify( and( aRef, bRef, aRef ), "AND(?0:ROW.a, ?0:ROW.b)" );

        // and: remove true
        checkSimplify( and( aRef, bRef, trueLiteral ), "AND(?0:ROW.a, ?0:ROW.b)" );

        // and: false falsifies
        checkSimplify( and( aRef, bRef, falseLiteral ), "false" );

        // and: remove duplicate "not"s
        checkSimplify( and( not( aRef ), bRef, not( cRef ), not( aRef ) ), "AND(?0:ROW.b, NOT(?0:ROW.a), NOT(?0:ROW.c))" );

        // and: "not true" falsifies
        checkSimplify( and( not( aRef ), bRef, not( trueLiteral ) ), "false" );

        // and: flatten and remove duplicates
        checkSimplify(
                and( aRef, and( and( bRef, not( cRef ), dRef, not( eRef ) ), not( eRef ) ) ),
                "AND(?0:ROW.a, ?0:ROW.b, ?0:ROW.d, NOT(?0:ROW.c), NOT(?0:ROW.e))" );

        // and: expand "... and not(or(x, y))" to "... and not(x) and not(y)"
        checkSimplify(
                and( aRef, bRef, not( or( cRef, or( dRef, eRef ) ) ) ),
                "AND(?0:ROW.a, ?0:ROW.b, NOT(?0:ROW.c), NOT(?0:ROW.d), NOT(?0:ROW.e))" );

        checkSimplify(
                and( aRef, bRef, not( or( not( cRef ), dRef, not( eRef ) ) ) ),
                "AND(?0:ROW.a, ?0:ROW.b, ?0:ROW.c, ?0:ROW.e, NOT(?0:ROW.d))" );

        // or: remove duplicates
        checkSimplify( or( aRef, bRef, aRef ), "OR(?0:ROW.a, ?0:ROW.b)" );

        // or: remove false
        checkSimplify( or( aRef, bRef, falseLiteral ), "OR(?0:ROW.a, ?0:ROW.b)" );

        // or: true makes everything true
        checkSimplify( or( aRef, bRef, trueLiteral ), "true" );

        // case: remove false branches
        checkSimplify(
                case_( eq( bRef, cRef ), dRef, falseLiteral, aRef, eRef ),
                "OR(AND(=(?0:ROW.b, ?0:ROW.c), ?0:ROW.d), AND(?0:ROW.e, <>(?0:ROW.b, ?0:ROW.c)))" );

        // case: true branches become the last branch
        checkSimplify(
                case_( eq( bRef, cRef ), dRef, trueLiteral, aRef, eq( cRef, dRef ), eRef, cRef ),
                "OR(AND(=(?0:ROW.b, ?0:ROW.c), ?0:ROW.d), AND(?0:ROW.a, <>(?0:ROW.b, ?0:ROW.c)))" );

        // case: singleton
        checkSimplify(
                case_( trueLiteral, aRef, eq( cRef, dRef ), eRef, cRef ),
                "?0:ROW.a" );

        // case: always same value
        checkSimplify(
                case_( aRef, literal1, bRef, literal1, cRef, literal1, dRef, literal1, literal1 ),
                "1" );

        // case: trailing false and null, no simplification
        checkSimplify3(
                case_( aRef, trueLiteral, bRef, trueLiteral, cRef, falseLiteral, nullBool ),
                "OR(?0:ROW.a, ?0:ROW.b, AND(null, NOT(?0:ROW.a), NOT(?0:ROW.b), NOT(?0:ROW.c)))",
                "OR(?0:ROW.a, ?0:ROW.b)",
                "OR(?0:ROW.a, ?0:ROW.b, NOT(?0:ROW.c))" );

        // case: form an AND of branches that return true
        checkSimplify(
                case_( aRef, trueLiteral, bRef, falseLiteral, cRef, falseLiteral, dRef, trueLiteral, falseLiteral ),
                "OR(?0:ROW.a, AND(?0:ROW.d, NOT(?0:ROW.b), NOT(?0:ROW.c)))" );

        checkSimplify(
                case_( aRef, trueLiteral, bRef, falseLiteral, cRef, falseLiteral, dRef, trueLiteral, eRef, falseLiteral, trueLiteral ),
                "OR(?0:ROW.a, AND(?0:ROW.d, NOT(?0:ROW.b), NOT(?0:ROW.c)), AND(NOT(?0:ROW.b), NOT(?0:ROW.c), NOT(?0:ROW.e)))" );

        checkSimplify(
                case_( eq( falseLiteral, falseLiteral ), falseLiteral, eq( falseLiteral, falseLiteral ), trueLiteral, trueLiteral ),
                "false" );

        // is null, applied to not-null value
        checkSimplify(
                rexBuilder.makeCall( OperatorRegistry.get( OperatorName.IS_NULL ), aRef ),
                "false" );

        // is not null, applied to not-null value
        checkSimplify(
                rexBuilder.makeCall( OperatorRegistry.get( OperatorName.IS_NOT_NULL ), aRef ),
                "true" );

        // condition, and the inverse - nothing to do due to null values
        checkSimplify2(
                and( le( hRef, literal1 ), gt( hRef, literal1 ) ),
                "AND(<=(?0:ROW.h, 1), >(?0:ROW.h, 1))",
                "false" );

        checkSimplify2(
                and( le( hRef, literal1 ), ge( hRef, literal1 ) ),
                "AND(<=(?0:ROW.h, 1), >=(?0:ROW.h, 1))",
                "=(?0:ROW.h, 1)" );

        checkSimplify2(
                and( lt( hRef, literal1 ), eq( hRef, literal1 ), ge( hRef, literal1 ) ),
                "AND(<(?0:ROW.h, 1), =(?0:ROW.h, 1), >=(?0:ROW.h, 1))",
                "false" );

        checkSimplify(
                and( lt( hRef, literal1 ), or( falseLiteral, falseLiteral ) ),
                "false" );
        checkSimplify(
                and( lt( hRef, literal1 ), or( falseLiteral, gt( jRef, kRef ) ) ),
                "AND(<(?0:ROW.h, 1), >(?0:ROW.j, ?0:ROW.k))" );
        checkSimplify(
                or( lt( hRef, literal1 ), and( trueLiteral, trueLiteral ) ),
                "true" );
        checkSimplify(
                or( lt( hRef, literal1 ), and( trueLiteral, or( trueLiteral, falseLiteral ) ) ),
                "true" );
        checkSimplify(
                or( lt( hRef, literal1 ), and( trueLiteral, and( trueLiteral, falseLiteral ) ) ),
                "<(?0:ROW.h, 1)" );
        checkSimplify(
                or( lt( hRef, literal1 ), and( trueLiteral, or( falseLiteral, falseLiteral ) ) ),
                "<(?0:ROW.h, 1)" );

        // "x = x" simplifies to "x is not null"
        checkSimplify( eq( literal1, literal1 ), "true" );
        checkSimplify( eq( hRef, hRef ), "true" );
        checkSimplify2( eq( iRef, iRef ), "=(?0:ROW.i, ?0:ROW.i)", "IS NOT NULL(?0:ROW.i)" );
        checkSimplifyUnchanged( eq( iRef, hRef ) );

        // "x <= x" simplifies to "x is not null"
        checkSimplify( le( literal1, literal1 ), "true" );
        checkSimplify( le( hRef, hRef ), "true" );
        checkSimplify2( le( iRef, iRef ), "<=(?0:ROW.i, ?0:ROW.i)", "IS NOT NULL(?0:ROW.i)" );
        checkSimplifyUnchanged( le( iRef, hRef ) );

        // "x >= x" simplifies to "x is not null"
        checkSimplify( ge( literal1, literal1 ), "true" );
        checkSimplify( ge( hRef, hRef ), "true" );
        checkSimplify2( ge( iRef, iRef ), ">=(?0:ROW.i, ?0:ROW.i)", "IS NOT NULL(?0:ROW.i)" );
        checkSimplifyUnchanged( ge( iRef, hRef ) );

        // "x != x" simplifies to "false"
        checkSimplify( ne( literal1, literal1 ), "false" );
        checkSimplify( ne( hRef, hRef ), "false" );
        checkSimplify2( ne( iRef, iRef ), "<>(?0:ROW.i, ?0:ROW.i)", "false" );
        checkSimplifyUnchanged( ne( iRef, hRef ) );

        // "x < x" simplifies to "false"
        checkSimplify( lt( literal1, literal1 ), "false" );
        checkSimplify( lt( hRef, hRef ), "false" );
        checkSimplify2( lt( iRef, iRef ), "<(?0:ROW.i, ?0:ROW.i)", "false" );
        checkSimplifyUnchanged( lt( iRef, hRef ) );

        // "x > x" simplifies to "false"
        checkSimplify( gt( literal1, literal1 ), "false" );
        checkSimplify( gt( hRef, hRef ), "false" );
        checkSimplify2( gt( iRef, iRef ), ">(?0:ROW.i, ?0:ROW.i)", "false" );
        checkSimplifyUnchanged( gt( iRef, hRef ) );

        // "(not x) is null" to "x is null"
        checkSimplify( isNull( not( vBool() ) ), "IS NULL(?0:ROW.bool0)" );
        checkSimplify( isNull( not( vBoolNotNull() ) ), "false" );

        // "(not x) is not null" to "x is not null"
        checkSimplify( isNotNull( not( vBool() ) ), "IS NOT NULL(?0:ROW.bool0)" );
        checkSimplify( isNotNull( not( vBoolNotNull() ) ), "true" );

        // "null is null" to "true"
        checkSimplify( isNull( nullBool ), "true" );
        // "(x + y) is null" simplifies to "x is null or y is null"
        checkSimplify( isNull( plus( vInt( 0 ), vInt( 1 ) ) ), "OR(IS NULL(?0:ROW.int0), IS NULL(?0:ROW.int1))" );
        checkSimplify( isNull( plus( vInt( 0 ), vIntNotNull( 1 ) ) ), "IS NULL(?0:ROW.int0)" );
        checkSimplify( isNull( plus( vIntNotNull( 0 ), vIntNotNull( 1 ) ) ), "false" );
        checkSimplify( isNull( plus( vIntNotNull( 0 ), vInt( 1 ) ) ), "IS NULL(?0:ROW.int1)" );

        // "(x + y) is not null" simplifies to "x is not null and y is not null"
        checkSimplify( isNotNull( plus( vInt( 0 ), vInt( 1 ) ) ), "AND(IS NOT NULL(?0:ROW.int0), IS NOT NULL(?0:ROW.int1))" );
        checkSimplify( isNotNull( plus( vInt( 0 ), vIntNotNull( 1 ) ) ), "IS NOT NULL(?0:ROW.int0)" );
        checkSimplify( isNotNull( plus( vIntNotNull( 0 ), vIntNotNull( 1 ) ) ), "true" );
        checkSimplify( isNotNull( plus( vIntNotNull( 0 ), vInt( 1 ) ) ), "IS NOT NULL(?0:ROW.int1)" );
    }


    @Test
    public void simplifyStrong() {
        checkSimplify( ge( trueLiteral, falseLiteral ), "true" );
        checkSimplify3( ge( trueLiteral, nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( ge( nullBool, nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( gt( trueLiteral, nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( le( trueLiteral, nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( lt( trueLiteral, nullBool ), "null:BOOLEAN", "false", "true" );

        checkSimplify3( not( nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( ne( vInt(), nullBool ), "null:BOOLEAN", "false", "true" );
        checkSimplify3( eq( vInt(), nullBool ), "null:BOOLEAN", "false", "true" );

        checkSimplify( plus( vInt(), nullInt ), "null:INTEGER" );
        checkSimplify( sub( vInt(), nullInt ), "null:INTEGER" );
        checkSimplify( mul( vInt(), nullInt ), "null:INTEGER" );
        checkSimplify( div( vInt(), nullInt ), "null:INTEGER" );
    }


    @Test
    public void testSimplifyFilter() {
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, intType )
                .add( "b", null, intType )
                .add( "c", null, booleanType )
                .add( "d", null, booleanType )
                .add( "e", null, booleanType )
                .add( "f", null, booleanType )
                .add( "g", null, booleanType )
                .add( "h", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode dRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode eRef = rexBuilder.makeFieldAccess( range, 4 );
        final RexNode fRef = rexBuilder.makeFieldAccess( range, 5 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral literal5 = rexBuilder.makeExactLiteral( new BigDecimal( 5 ) );
        final RexLiteral literal10 = rexBuilder.makeExactLiteral( BigDecimal.TEN );

        // condition, and the inverse
        checkSimplifyFilter( and( le( aRef, literal1 ), gt( aRef, literal1 ) ), "false" );

        checkSimplifyFilter( and( le( aRef, literal1 ), ge( aRef, literal1 ) ), "=(?0:ROW.a, 1)" );

        checkSimplifyFilter( and( lt( aRef, literal1 ), eq( aRef, literal1 ), ge( aRef, literal1 ) ), "false" );

        // simplify equals boolean
        final ImmutableList<RexNode> args = ImmutableList.of( eq( eq( aRef, literal1 ), trueLiteral ), eq( bRef, literal1 ) );
        checkSimplifyFilter( and( args ), "AND(=(?0:ROW.a, 1), =(?0:ROW.b, 1))" );

        // as previous, using simplifyFilterPredicates
        assertThat(
                simplify.simplifyFilterPredicates( args ).toString(),
                equalTo( "AND(=(?0:ROW.a, 1), =(?0:ROW.b, 1))" ) );

        // "a = 1 and a = 10" is always false
        final ImmutableList<RexNode> args2 = ImmutableList.of( eq( aRef, literal1 ), eq( aRef, literal10 ) );
        checkSimplifyFilter( and( args2 ), "false" );

        assertThat( simplify.simplifyFilterPredicates( args2 ), nullValue() );

        // equality on constants, can remove the equality on the variables
        checkSimplifyFilter( and( eq( aRef, literal1 ), eq( bRef, literal1 ), eq( aRef, bRef ) ), "AND(=(?0:ROW.a, 1), =(?0:ROW.b, 1))" );

        // condition not satisfiable
        checkSimplifyFilter( and( eq( aRef, literal1 ), eq( bRef, literal10 ), eq( aRef, bRef ) ), "false" );

        // condition not satisfiable
        checkSimplifyFilter( and( gt( aRef, literal10 ), ge( bRef, literal1 ), lt( aRef, literal10 ) ), "false" );

        // one "and" containing three "or"s
        checkSimplifyFilter(
                or( gt( aRef, literal10 ), gt( bRef, literal1 ), gt( aRef, literal10 ) ),
                "OR(>(?0:ROW.a, 10), >(?0:ROW.b, 1))" );

        // case: trailing false and null, remove
        checkSimplifyFilter(
                case_( cRef, trueLiteral, dRef, trueLiteral, eRef, falseLiteral, fRef, falseLiteral, nullBool ),
                "OR(?0:ROW.c, ?0:ROW.d)" );

        // condition with null value for range
        checkSimplifyFilter( and( gt( aRef, nullBool ), ge( bRef, literal1 ) ), "false" );

        // condition "1 < a && 5 < x" yields "5 < x"
        checkSimplifyFilter(
                and( lt( literal1, aRef ), lt( literal5, aRef ) ),
                AlgOptPredicateList.EMPTY,
                "<(5, ?0:ROW.a)" );

        // condition "1 < a && a < 5" is unchanged
        checkSimplifyFilter(
                and( lt( literal1, aRef ), lt( aRef, literal5 ) ),
                AlgOptPredicateList.EMPTY,
                "AND(<(1, ?0:ROW.a), <(?0:ROW.a, 5))" );

        // condition "1 > a && 5 > x" yields "1 > a"
        checkSimplifyFilter(
                and( gt( literal1, aRef ), gt( literal5, aRef ) ),
                AlgOptPredicateList.EMPTY,
                ">(1, ?0:ROW.a)" );

        // condition "1 > a && a > 5" yields false
        checkSimplifyFilter(
                and( gt( literal1, aRef ), gt( aRef, literal5 ) ),
                AlgOptPredicateList.EMPTY,
                "false" );

        // range with no predicates;
        // condition "a > 1 && a < 10 && a < 5" yields "a < 1 && a < 5"
        checkSimplifyFilter(
                and( gt( aRef, literal1 ), lt( aRef, literal10 ), lt( aRef, literal5 ) ),
                AlgOptPredicateList.EMPTY,
                "AND(>(?0:ROW.a, 1), <(?0:ROW.a, 5))" );

        // condition "a > 1 && a < 10 && a < 5"
        // with pre-condition "a > 5"
        // yields "false"
        checkSimplifyFilter(
                and( gt( aRef, literal1 ), lt( aRef, literal10 ), lt( aRef, literal5 ) ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( gt( aRef, literal5 ) ) ),
                "false" );

        // condition "a > 1 && a < 10 && a <= 5"
        // with pre-condition "a >= 5"
        // yields "a = 5"
        // "a <= 5" would also be correct, just a little less concise.
        checkSimplifyFilter(
                and( gt( aRef, literal1 ), lt( aRef, literal10 ), le( aRef, literal5 ) ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( ge( aRef, literal5 ) ) ),
                "=(?0:ROW.a, 5)" );

        // condition "a > 1 && a < 10 && a < 5"
        // with pre-condition "b < 10 && a > 5"
        // yields "a > 1 and a < 5"
        checkSimplifyFilter(
                and( gt( aRef, literal1 ), lt( aRef, literal10 ), lt( aRef, literal5 ) ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( lt( bRef, literal10 ), ge( aRef, literal1 ) ) ),
                "AND(>(?0:ROW.a, 1), <(?0:ROW.a, 5))" );

        // condition "a > 1"
        // with pre-condition "b < 10 && a > 5"
        // yields "true"
        checkSimplifyFilter(
                gt( aRef, literal1 ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( lt( bRef, literal10 ), gt( aRef, literal5 ) ) ),
                "true" );

        // condition "a < 1"
        // with pre-condition "b < 10 && a > 5"
        // yields "false"
        checkSimplifyFilter(
                lt( aRef, literal1 ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( lt( bRef, literal10 ), gt( aRef, literal5 ) ) ),
                "false" );

        // condition "a > 5"
        // with pre-condition "b < 10 && a >= 5"
        // yields "a > 5"
        checkSimplifyFilter(
                gt( aRef, literal5 ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( lt( bRef, literal10 ), ge( aRef, literal5 ) ) ),
                ">(?0:ROW.a, 5)" );

        // condition "a > 5"
        // with pre-condition "a <= 5"
        // yields "false"
        checkSimplifyFilter(
                gt( aRef, literal5 ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( le( aRef, literal5 ) ) ),
                "false" );

        // condition "a > 5"
        // with pre-condition "a <= 5 and b <= 5"
        // yields "false"
        checkSimplifyFilter(
                gt( aRef, literal5 ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( le( aRef, literal5 ), le( bRef, literal5 ) ) ),
                "false" );

        // condition "a > 5 or b > 5"
        // with pre-condition "a <= 5 and b <= 5"
        // should yield "false" but yields "a = 5 or b = 5"
        checkSimplifyFilter(
                or( gt( aRef, literal5 ), gt( bRef, literal5 ) ),
                AlgOptPredicateList.of( rexBuilder, ImmutableList.of( le( aRef, literal5 ), le( bRef, literal5 ) ) ),
                "false" );
    }


    @Test
    public void testSimplifyAndPush() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, intType )
                .add( "b", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral literal5 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 5 ) );
        final RexLiteral literal10 = rexBuilder.makeExactLiteral( BigDecimal.TEN );

        checkSimplifyFilter(
                or(
                        or( eq( aRef, literal1 ), eq( aRef, literal1 ) ),
                        eq( aRef, literal1 ) ),
                "=(?0:ROW.a, 1)" );

        checkSimplifyFilter(
                or(
                        and( eq( aRef, literal1 ), eq( aRef, literal1 ) ),
                        and( eq( aRef, literal10 ), eq( aRef, literal1 ) ) ),
                "=(?0:ROW.a, 1)" );

        checkSimplifyFilter(
                and(
                        eq( aRef, literal1 ),
                        or( eq( aRef, literal1 ), eq( aRef, literal10 ) ) ),
                "=(?0:ROW.a, 1)" );
        checkSimplifyFilter(
                and(
                        or( eq( aRef, literal1 ), eq( aRef, literal10 ) ),
                        eq( aRef, literal1 ) ),
                "=(?0:ROW.a, 1)" );

        checkSimplifyFilter(
                and( gt( aRef, literal10 ), gt( aRef, literal1 ) ),
                ">(?0:ROW.a, 10)" );

        checkSimplifyFilter(
                and( gt( aRef, literal1 ), gt( aRef, literal10 ) ),
                ">(?0:ROW.a, 10)" );

        // "null AND NOT(null OR x)" => "null AND NOT(x)"
        checkSimplify2(
                and( nullBool, not( or( nullBool, vBool() ) ) ),
                "AND(null, NOT(?0:ROW.bool0))",
                "false" );

        // "x1 AND x2 AND x3 AND NOT(x1) AND NOT(x2) AND NOT(x0)" =>
        // "x3 AND null AND x1 IS NULL AND x2 IS NULL AND NOT(x0)"
        checkSimplify2(
                and( vBool( 1 ), vBool( 2 ), vBool( 3 ), not( vBool( 1 ) ), not( vBool( 2 ) ), not( vBool() ) ),
                "AND(?0:ROW.bool3, null, IS NULL(?0:ROW.bool1), IS NULL(?0:ROW.bool2), NOT(?0:ROW.bool0))",
                "false" );
    }


    @Test
    public void testSimplifyOrTerms() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, intType ).nullable( false )
                .add( "b", null, intType ).nullable( true )
                .add( "c", null, intType ).nullable( true )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral literal2 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 2 ) );
        final RexLiteral literal3 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 3 ) );
        final RexLiteral literal4 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 4 ) );

        // "a != 1 or a = 1" ==> "true"
        checkSimplifyFilter(
                or( ne( aRef, literal1 ), eq( aRef, literal1 ) ),
                "true" );

        // TODO: make this simplify to "true"
        checkSimplifyFilter(
                or( eq( aRef, literal1 ), ne( aRef, literal1 ) ),
                "OR(=(?0:ROW.a, 1), <>(?0:ROW.a, 1))" );

        // "b != 1 or b = 1" cannot be simplified, because b might be null
        final RexNode neOrEq = or( ne( bRef, literal1 ), eq( bRef, literal1 ) );
        checkSimplifyFilter( neOrEq, "OR(<>(?0:ROW.b, 1), =(?0:ROW.b, 1))" );

        // Careful of the excluded middle!
        // We cannot simplify "b != 1 or b = 1" to "true" because if b is null, the result is unknown.
        // TODO: "b is not unknown" would be the best simplification.
        final RexNode simplified = this.simplify.simplifyUnknownAs( neOrEq, RexUnknownAs.UNKNOWN );
        assertThat( simplified.toString(), equalTo( "OR(<>(?0:ROW.b, 1), =(?0:ROW.b, 1))" ) );

        // "a is null or a is not null" ==> "true"
        checkSimplifyFilter(
                or( isNull( aRef ), isNotNull( aRef ) ),
                "true" );

        // "a is not null or a is null" ==> "true"
        checkSimplifyFilter(
                or( isNotNull( aRef ), isNull( aRef ) ),
                "true" );

        // "b is not null or b is null" ==> "true" (valid even though b nullable)
        checkSimplifyFilter(
                or( isNotNull( bRef ), isNull( bRef ) ),
                "true" );

        // "b is not null or c is null" unchanged
        checkSimplifyFilter(
                or( isNotNull( bRef ), isNull( cRef ) ),
                "OR(IS NOT NULL(?0:ROW.b), IS NULL(?0:ROW.c))" );

        // "b is null or b is not false" unchanged
        checkSimplifyFilter(
                or( isNull( bRef ), isNotFalse( bRef ) ),
                "OR(IS NULL(?0:ROW.b), IS NOT FALSE(?0:ROW.b))" );

        // multiple predicates are handled correctly
        checkSimplifyFilter(
                and(
                        or( eq( bRef, literal1 ), eq( bRef, literal2 ) ),
                        eq( bRef, literal2 ),
                        eq( aRef, literal3 ),
                        or( eq( aRef, literal3 ), eq( aRef, literal4 ) ) ),
                "AND(=(?0:ROW.b, 2), =(?0:ROW.a, 3))" );

        checkSimplify3(
                or( lt( vInt(), nullInt ), ne( literal( 0 ), vInt() ) ),
                "OR(null, <>(0, ?0:ROW.int0))",
                "<>(0, ?0:ROW.int0)",
                "true" );
    }


    @Test
    public void testSimplifyNotAnd() {
        final RexNode e = or(
                le(
                        vBool( 1 ),
                        literal( true ) ),
                eq(
                        literal( false ),
                        eq( literal( false ), vBool( 1 ) ) ) );
        checkSimplifyUnchanged( e );
    }


    @Test
    public void testSimplifyUnknown() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, intType ).nullable( true )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );

        checkSimplify2(
                and( eq( aRef, literal1 ), nullInt ),
                "AND(=(?0:ROW.a, 1), null:INTEGER)",
                "false" );
        checkSimplify2(
                and( trueLiteral, nullBool ),
                "null:BOOLEAN",
                "false" );
        checkSimplify(
                and( falseLiteral, nullBool ),
                "false" );

        checkSimplify2(
                and( nullBool, eq( aRef, literal1 ) ),
                "AND(null, =(?0:ROW.a, 1))",
                "false" );

        checkSimplify3(
                or( eq( aRef, literal1 ), nullBool ),
                "OR(=(?0:ROW.a, 1), null)",
                "=(?0:ROW.a, 1)",
                "true" );
        checkSimplify(
                or( trueLiteral, nullBool ),
                "true" );
        checkSimplify3(
                or( falseLiteral, nullBool ),
                "null:BOOLEAN",
                "false",
                "true" );
    }


    @Test
    public void testSimplifyAnd3() {
        final AlgDataType boolType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, boolType ).nullable( true )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );

        // in the case of 3-valued logic, the result must be unknown if a is unknown
        checkSimplify2(
                and( aRef, not( aRef ) ),
                "AND(null, IS NULL(?0:ROW.a))",
                "false" );
    }


    @Test
    public void fieldAccessEqualsHashCode() {
        assertEquals( "vBool() instances should be equal", vBool(), vBool() );
        assertEquals( "vBool().hashCode()", vBool().hashCode(), vBool().hashCode() );
        assertNotSame( "vBool() is expected to produce new RexFieldAccess", vBool(), vBool() );
        assertNotEquals( "vBool(0) != vBool(1)", vBool( 0 ), vBool( 1 ) );
    }


    @Test
    public void testSimplifyDynamicParam() {
        checkSimplify( or( vBool(), vBool() ), "?0:ROW.bool0" );
    }


    /**
     * Unit test for "RexUtil.simplifyCase() should account for nullability".
     */
    @Test
    public void testSimplifyCaseNotNullableBoolean() {
        RexNode condition = eq( vVarchar(), literal( "S" ) );
        RexCall caseNode = (RexCall) case_( condition, trueLiteral, falseLiteral );

        final RexCall result = (RexCall) simplify.simplifyUnknownAs( caseNode, RexUnknownAs.UNKNOWN );
        assertThat( "The case should be nonNullable", caseNode.getType().isNullable(), is( false ) );
        assertThat( "Expected a nonNullable type", result.getType().isNullable(), is( false ) );
        assertThat( result.getType().getPolyType(), is( PolyType.BOOLEAN ) );
        assertThat( result.getOperator(), is( OperatorRegistry.get( OperatorName.IS_TRUE ) ) );
        assertThat( result.getOperands().get( 0 ), is( condition ) );
    }


    @Test
    public void testSimplifyCaseNullableBoolean() {
        RexNode condition = eq( input( tVarchar(), 0 ), literal( "S" ) );
        RexNode caseNode = case_( condition, trueLiteral, falseLiteral );

        RexCall result = (RexCall) simplify.simplifyUnknownAs( caseNode, RexUnknownAs.UNKNOWN );
        assertThat( result.getType().isNullable(), is( false ) );
        assertThat( result.getType().getPolyType(), is( PolyType.BOOLEAN ) );
        assertThat( result, is( condition ) );
    }


    @Test
    public void testSimplifyCaseBranchesCollapse() {
        // case when x is true then 1 when x is not true then 1 else 2 end
        // => case when x is true or x is not true then 1 else 2 end
        checkSimplify(
                case_(
                        isTrue( vBool() ),
                        literal( 1 ),
                        isNotTrue( vBool() ),
                        literal( 1 ),
                        literal( 2 ) ),
                "CASE(OR(IS TRUE(?0:ROW.bool0), IS NOT TRUE(?0:ROW.bool0)), 1, 2)" );
    }


    @Test
    public void testSimplifyCaseBranchesCollapse2() {
        // case when x is true then 1 when true then 1 else 2 end
        // => 1
        checkSimplify(
                case_(
                        isTrue( vBool() ),
                        literal( 1 ),
                        trueLiteral,
                        literal( 1 ),
                        literal( 2 ) ),
                "1" );
    }


    @Test
    public void testSimplifyCaseNullableVarChar() {
        RexNode condition = eq( input( tVarchar(), 0 ), literal( "S" ) );
        RexNode caseNode = case_( condition, literal( "A" ), literal( "B" ) );

        RexCall result = (RexCall) simplify.simplifyUnknownAs( caseNode, RexUnknownAs.UNKNOWN );
        assertThat( result.getType().isNullable(), is( false ) );
        assertThat( result.getType().getPolyType(), is( PolyType.CHAR ) );
        assertThat( result, is( caseNode ) );
    }


    @Test
    public void testSimplifyCaseCasting() {
        RexNode caseNode = case_( eq( vIntNotNull(), literal( 3 ) ), nullBool, falseLiteral );

        checkSimplify3(
                caseNode,
                "AND(=(?0:ROW.notNullInt0, 3), null)",
                "false",
                "=(?0:ROW.notNullInt0, 3)" );
    }


    @Test
    public void testSimplifyCaseAndNotSimplicationIsInAction() {
        RexNode caseNode = case_(
                eq( vIntNotNull(), literal( 0 ) ),
                falseLiteral,
                eq( vIntNotNull(), literal( 1 ) ),
                trueLiteral,
                falseLiteral );
        checkSimplify( caseNode, "=(?0:ROW.notNullInt0, 1)" );
    }


    @Test
    public void testSimplifyCaseBranchRemovalStrengthensType() {
        RexNode caseNode = case_( falseLiteral, nullBool, eq( div( vInt(), literal( 2 ) ), literal( 3 ) ), trueLiteral, falseLiteral );
        assertThat( "Expected to have a nullable type for " + caseNode + ".", caseNode.getType().isNullable(), is( true ) );
        RexNode res = simplify.simplify( caseNode );
        assertThat( "Expected to have a nonNullable type for " + res + ".", res.getType().isNullable(), is( false ) );
    }


    @Test
    public void testSimplifyCaseCompaction() {
        RexNode caseNode = case_( vBool( 0 ), vInt( 0 ), vBool( 1 ), vInt( 0 ), vInt( 1 ) );
        checkSimplify( caseNode, "CASE(OR(?0:ROW.bool0, ?0:ROW.bool1), ?0:ROW.int0, ?0:ROW.int1)" );
    }


    @Test
    public void testSimplifyCaseCompaction2() {
        RexNode caseNode = case_( vBool( 0 ), vInt( 0 ), vBool( 1 ), vInt( 1 ), vInt( 1 ) );
        checkSimplify( caseNode, "CASE(?0:ROW.bool0, ?0:ROW.int0, ?0:ROW.int1)" );
    }


    @Test
    public void testSimplifyCaseCompactionDiv() {
        // FIXME: RexInterpreter currently evaluates children beforehand.
        simplify = simplify.withParanoid( false );
        RexNode caseNode = case_( vBool( 0 ), vInt( 0 ),
                eq( div( literal( 3 ), vIntNotNull() ), literal( 11 ) ),
                vInt( 0 ),
                vInt( 1 ) );
        // expectation here is that the 2 branches are not merged.
        checkSimplifyUnchanged( caseNode );
    }


    /**
     * Tests a CASE value branch that contains division.
     */
    @Test
    public void testSimplifyCaseDiv1() {
        // FIXME: RexInterpreter currently evaluates children beforehand.
        simplify = simplify.withParanoid( false );
        RexNode caseNode = case_(
                ne( vIntNotNull(), literal( 0 ) ),
                eq( div( literal( 3 ), vIntNotNull() ), literal( 11 ) ),
                falseLiteral );
        checkSimplifyUnchanged( caseNode );
    }


    /**
     * Tests a CASE condition that contains division,
     */
    @Test
    public void testSimplifyCaseDiv2() {
        // FIXME: RexInterpreter currently evaluates children beforehand.
        simplify = simplify.withParanoid( false );
        RexNode caseNode = case_(
                eq( vIntNotNull(), literal( 0 ) ),
                trueLiteral,
                gt( div( literal( 3 ), vIntNotNull() ), literal( 1 ) ),
                trueLiteral,
                falseLiteral );
        checkSimplifyUnchanged( caseNode );
    }


    @Test
    public void testSimplifyAnd() {
        AlgDataType booleanNotNullableType = typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.BOOLEAN ), false );
        AlgDataType booleanNullableType = typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.BOOLEAN ), true );
        RexNode andCondition = and(
                rexBuilder.makeInputRef( booleanNotNullableType, 0 ),
                rexBuilder.makeInputRef( booleanNullableType, 1 ),
                rexBuilder.makeInputRef( booleanNotNullableType, 2 ) );
        RexNode result = simplify.simplifyUnknownAs( andCondition, RexUnknownAs.UNKNOWN );
        assertThat( result.getType().isNullable(), is( true ) );
        assertThat( result.getType().getPolyType(), is( PolyType.BOOLEAN ) );
    }


    @Test
    public void testSimplifyIsNotNull() {
        AlgDataType intType = typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.INTEGER ), false );
        AlgDataType intNullableType = typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.INTEGER ), true );
        final RexInputRef i0 = rexBuilder.makeInputRef( intNullableType, 0 );
        final RexInputRef i1 = rexBuilder.makeInputRef( intNullableType, 1 );
        final RexInputRef i2 = rexBuilder.makeInputRef( intType, 2 );
        final RexInputRef i3 = rexBuilder.makeInputRef( intType, 3 );
        final RexLiteral one = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral null_ = rexBuilder.makeNullLiteral( intType );
        checkSimplify( isNotNull( lt( i0, i1 ) ), "AND(IS NOT NULL($0), IS NOT NULL($1))" );
        checkSimplify( isNotNull( lt( i0, i2 ) ), "IS NOT NULL($0)" );
        checkSimplify( isNotNull( lt( i2, i3 ) ), "true" );
        checkSimplify( isNotNull( lt( i0, one ) ), "IS NOT NULL($0)" );
        checkSimplify( isNotNull( lt( i0, null_ ) ), "false" );
    }


    @Test
    public void checkSimplifyDynamicParam() {
        checkSimplify( isNotNull( lt( vInt( 0 ), vInt( 1 ) ) ), "AND(IS NOT NULL(?0:ROW.int0), IS NOT NULL(?0:ROW.int1))" );
        checkSimplify( isNotNull( lt( vInt( 0 ), vIntNotNull( 2 ) ) ), "IS NOT NULL(?0:ROW.int0)" );
        checkSimplify( isNotNull( lt( vIntNotNull( 2 ), vIntNotNull( 3 ) ) ), "true" );
        checkSimplify( isNotNull( lt( vInt( 0 ), literal( BigDecimal.ONE ) ) ), "IS NOT NULL(?0:ROW.int0)" );
        checkSimplify( isNotNull( lt( vInt( 0 ), null_( tInt() ) ) ), "false" );
    }


    @Test
    public void testSimplifyCastLiteral() {
        final List<RexLiteral> literals = new ArrayList<>();
        literals.add( rexBuilder.makeExactLiteral( BigDecimal.ONE, typeFactory.createPolyType( PolyType.INTEGER ) ) );
        literals.add( rexBuilder.makeExactLiteral( BigDecimal.valueOf( 2 ), typeFactory.createPolyType( PolyType.BIGINT ) ) );
        literals.add( rexBuilder.makeExactLiteral( BigDecimal.valueOf( 3 ), typeFactory.createPolyType( PolyType.SMALLINT ) ) );
        literals.add( rexBuilder.makeExactLiteral( BigDecimal.valueOf( 4 ), typeFactory.createPolyType( PolyType.TINYINT ) ) );
        literals.add( rexBuilder.makeExactLiteral( new BigDecimal( "1234" ), typeFactory.createPolyType( PolyType.DECIMAL, 4, 0 ) ) );
        literals.add( rexBuilder.makeExactLiteral( new BigDecimal( "123.45" ), typeFactory.createPolyType( PolyType.DECIMAL, 5, 2 ) ) );
        literals.add( rexBuilder.makeApproxLiteral( new BigDecimal( "3.1415" ), typeFactory.createPolyType( PolyType.REAL ) ) );
        literals.add( rexBuilder.makeApproxLiteral( BigDecimal.valueOf( Math.E ), typeFactory.createPolyType( PolyType.FLOAT ) ) );
        literals.add( rexBuilder.makeApproxLiteral( BigDecimal.valueOf( Math.PI ), typeFactory.createPolyType( PolyType.DOUBLE ) ) );
        literals.add( rexBuilder.makeLiteral( true ) );
        literals.add( rexBuilder.makeLiteral( false ) );
        literals.add( rexBuilder.makeLiteral( "hello world" ) );
        literals.add( rexBuilder.makeLiteral( "1969-07-20 12:34:56" ) );
        literals.add( rexBuilder.makeLiteral( "1969-07-20" ) );
        literals.add( rexBuilder.makeLiteral( "12:34:45" ) );
        literals.add( (RexLiteral) rexBuilder.makeLiteral( new ByteString( new byte[]{ 1, 2, -34, 0, -128 } ), typeFactory.createPolyType( PolyType.BINARY, 5 ), false ) );
        literals.add( rexBuilder.makeDateLiteral( new DateString( 1974, 8, 9 ) ) );
        literals.add( rexBuilder.makeTimeLiteral( new TimeString( 1, 23, 45 ), 0 ) );
        literals.add( rexBuilder.makeTimestampLiteral( new TimestampString( 1974, 8, 9, 1, 23, 45 ), 0 ) );

        final Multimap<PolyType, RexLiteral> map = LinkedHashMultimap.create();
        for ( RexLiteral literal : literals ) {
            map.put( literal.getTypeName(), literal );
        }

        final List<AlgDataType> types = new ArrayList<>();
        types.add( typeFactory.createPolyType( PolyType.INTEGER ) );
        types.add( typeFactory.createPolyType( PolyType.BIGINT ) );
        types.add( typeFactory.createPolyType( PolyType.SMALLINT ) );
        types.add( typeFactory.createPolyType( PolyType.TINYINT ) );
        types.add( typeFactory.createPolyType( PolyType.REAL ) );
        types.add( typeFactory.createPolyType( PolyType.FLOAT ) );
        types.add( typeFactory.createPolyType( PolyType.DOUBLE ) );
        types.add( typeFactory.createPolyType( PolyType.BOOLEAN ) );
        types.add( typeFactory.createPolyType( PolyType.VARCHAR, 10 ) );
        types.add( typeFactory.createPolyType( PolyType.CHAR, 5 ) );
        types.add( typeFactory.createPolyType( PolyType.VARBINARY, 60 ) );
        types.add( typeFactory.createPolyType( PolyType.BINARY, 3 ) );
        types.add( typeFactory.createPolyType( PolyType.TIMESTAMP ) );
        types.add( typeFactory.createPolyType( PolyType.TIME ) );
        types.add( typeFactory.createPolyType( PolyType.DATE ) );

        for ( AlgDataType fromType : types ) {
            for ( AlgDataType toType : types ) {
                if ( PolyTypeAssignmentRules.instance( false ).canCastFrom( toType.getPolyType(), fromType.getPolyType() ) ) {
                    for ( RexLiteral literal : map.get( fromType.getPolyType() ) ) {
                        final RexNode cast = rexBuilder.makeCast( toType, literal );
                        if ( cast instanceof RexLiteral ) {
                            assertThat( cast.getType(), is( toType ) );
                            continue; // makeCast already simplified
                        }
                        final RexNode simplified = simplify.simplifyUnknownAs( cast, RexUnknownAs.UNKNOWN );
                        boolean expectedSimplify =
                                literal.getTypeName() != toType.getPolyType()
                                        || (literal.getTypeName() == PolyType.CHAR
                                        && ((NlsString) literal.getValue()).getValue().length()
                                        > toType.getPrecision())
                                        || (literal.getTypeName() == PolyType.BINARY
                                        && ((ByteString) literal.getValue()).length()
                                        > toType.getPrecision());
                        boolean couldSimplify = !cast.equals( simplified );
                        final String reason = (expectedSimplify
                                ? "expected to simplify, but could not: "
                                : "simplified, but did not expect to: ")
                                + cast + " --> " + simplified;
                        assertThat( reason, couldSimplify, is( expectedSimplify ) );
                    }
                }
            }
        }
    }


    @Test
    public void testCastLiteral() {
        assertNode( "cast(literal int not null)", "42:INTEGER NOT NULL", cast( literal( 42 ), tInt() ) );
        assertNode( "cast(literal int)", "42:INTEGER NOT NULL", cast( literal( 42 ), nullable( tInt() ) ) );

        assertNode( "abstractCast(literal int not null)", "CAST(42):INTEGER NOT NULL", abstractCast( literal( 42 ), tInt() ) );
        assertNode( "abstractCast(literal int)", "CAST(42):INTEGER", abstractCast( literal( 42 ), nullable( tInt() ) ) );
    }


    @Test
    public void testSimplifyCastLiteral2() {
        final RexLiteral literalAbc = rexBuilder.makeLiteral( "abc" );
        final RexLiteral literalOne = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType varcharType = typeFactory.createPolyType( PolyType.VARCHAR, 10 );
        final AlgDataType booleanType = typeFactory.createPolyType( PolyType.BOOLEAN );
        final AlgDataType dateType = typeFactory.createPolyType( PolyType.DATE );
        final AlgDataType timestampType = typeFactory.createPolyType( PolyType.TIMESTAMP );
        checkSimplifyUnchanged( cast( literalAbc, intType ) );
        checkSimplifyUnchanged( cast( literalOne, intType ) );
        checkSimplifyUnchanged( cast( literalAbc, varcharType ) );
        checkSimplify( cast( literalOne, varcharType ), "'1':VARCHAR(10)" );
        checkSimplifyUnchanged( cast( literalAbc, booleanType ) );
        checkSimplify( cast( literalOne, booleanType ), "false" ); // different from Hive
        checkSimplifyUnchanged( cast( literalAbc, dateType ) );
        checkSimplify( cast( literalOne, dateType ), "1970-01-02" ); // different from Hive
        checkSimplifyUnchanged( cast( literalAbc, timestampType ) );
        checkSimplify( cast( literalOne, timestampType ), "1970-01-01 00:00:00" ); // different from Hive
    }


    @Test
    public void testSimplifyCastLiteral3() {
        // Default TimeZone is "America/Los_Angeles" (DummyDataContext)
        final RexLiteral literalDate = rexBuilder.makeDateLiteral( new DateString( "2011-07-20" ) );
        final RexLiteral literalTime = rexBuilder.makeTimeLiteral( new TimeString( "12:34:56" ), 0 );
        final RexLiteral literalTimestamp = rexBuilder.makeTimestampLiteral( new TimestampString( "2011-07-20 12:34:56" ), 0 );
        final RexLiteral literalTimeLTZ = rexBuilder.makeTimeWithLocalTimeZoneLiteral( new TimeString( 1, 23, 45 ), 0 );
        final RexLiteral timeLTZChar1 = rexBuilder.makeLiteral( "12:34:45 America/Los_Angeles" );
        final RexLiteral timeLTZChar2 = rexBuilder.makeLiteral( "12:34:45 UTC" );
        final RexLiteral timeLTZChar3 = rexBuilder.makeLiteral( "12:34:45 GMT+01" );
        final RexLiteral timestampLTZChar1 = rexBuilder.makeLiteral( "2011-07-20 12:34:56 Asia/Tokyo" );
        final RexLiteral timestampLTZChar2 = rexBuilder.makeLiteral( "2011-07-20 12:34:56 GMT+01" );
        final RexLiteral timestampLTZChar3 = rexBuilder.makeLiteral( "2011-07-20 12:34:56 UTC" );
        final RexLiteral literalTimestampLTZ = rexBuilder.makeTimestampWithLocalTimeZoneLiteral( new TimestampString( 2011, 7, 20, 8, 23, 45 ), 0 );

        final AlgDataType dateType = typeFactory.createPolyType( PolyType.DATE );
        final AlgDataType timeType = typeFactory.createPolyType( PolyType.TIME );
        final AlgDataType timestampType = typeFactory.createPolyType( PolyType.TIMESTAMP );
        final AlgDataType timeLTZType = typeFactory.createPolyType( PolyType.TIME_WITH_LOCAL_TIME_ZONE );
        final AlgDataType timestampLTZType = typeFactory.createPolyType( PolyType.TIMESTAMP_WITH_LOCAL_TIME_ZONE );
        final AlgDataType varCharType = typeFactory.createPolyType( PolyType.VARCHAR, 40 );

        checkSimplify( cast( timeLTZChar1, timeLTZType ), "20:34:45:TIME_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( timeLTZChar2, timeLTZType ), "12:34:45:TIME_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( timeLTZChar3, timeLTZType ), "11:34:45:TIME_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplifyUnchanged( cast( literalTimeLTZ, timeLTZType ) );
        checkSimplify( cast( timestampLTZChar1, timestampLTZType ), "2011-07-20 03:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( timestampLTZChar2, timestampLTZType ), "2011-07-20 11:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( timestampLTZChar3, timestampLTZType ), "2011-07-20 12:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplifyUnchanged( cast( literalTimestampLTZ, timestampLTZType ) );
        checkSimplify( cast( literalDate, timestampLTZType ), "2011-07-20 07:00:00:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( literalTime, timestampLTZType ), "2011-07-20 19:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( literalTimestamp, timestampLTZType ), "2011-07-20 19:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( literalTimestamp, dateType ), "2011-07-20" );
        checkSimplify( cast( literalTimestampLTZ, dateType ), "2011-07-20" );
        checkSimplify( cast( literalTimestampLTZ, timeType ), "01:23:45" );
        checkSimplify( cast( literalTimestampLTZ, timestampType ), "2011-07-20 01:23:45" );
        checkSimplify( cast( literalTimeLTZ, timeType ), "17:23:45" );
        checkSimplify( cast( literalTime, timeLTZType ), "20:34:56:TIME_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( literalTimestampLTZ, timeLTZType ), "08:23:45:TIME_WITH_LOCAL_TIME_ZONE(0)" );
        checkSimplify( cast( literalTimeLTZ, varCharType ), "'17:23:45 America/Los_Angeles':VARCHAR(40)" );
        checkSimplify( cast( literalTimestampLTZ, varCharType ), "'2011-07-20 01:23:45 America/Los_Angeles':VARCHAR(40)" );
        checkSimplify( cast( literalTimeLTZ, timestampType ), "2011-07-19 18:23:45" );
        checkSimplify( cast( literalTimeLTZ, timestampLTZType ), "2011-07-20 01:23:45:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)" );
    }


    @Test
    public void testRemovalOfNullabilityWideningCast() {
        RexNode expr = cast( isTrue( vBoolNotNull() ), tBoolean( true ) );
        assertThat( expr.getType().isNullable(), is( true ) );
        RexNode result = simplify.simplifyUnknownAs( expr, RexUnknownAs.UNKNOWN );
        assertThat( result.getType().isNullable(), is( false ) );
    }


    @Test
    public void testCompareTimestampWithTimeZone() {
        final TimestampWithTimeZoneString timestampLTZChar1 = new TimestampWithTimeZoneString( "2011-07-20 10:34:56 America/Los_Angeles" );
        final TimestampWithTimeZoneString timestampLTZChar2 = new TimestampWithTimeZoneString( "2011-07-20 19:34:56 Europe/Rome" );
        final TimestampWithTimeZoneString timestampLTZChar3 = new TimestampWithTimeZoneString( "2011-07-20 01:34:56 Asia/Tokyo" );
        final TimestampWithTimeZoneString timestampLTZChar4 = new TimestampWithTimeZoneString( "2011-07-20 10:34:56 America/Los_Angeles" );

        assertThat( timestampLTZChar1.equals( timestampLTZChar2 ), is( false ) );
        assertThat( timestampLTZChar1.equals( timestampLTZChar3 ), is( false ) );
        assertThat( timestampLTZChar1.equals( timestampLTZChar4 ), is( true ) );
    }


    @Test
    public void testSimplifyLiterals() {
        final RexLiteral literalAbc = rexBuilder.makeLiteral( "abc" );
        final RexLiteral literalDef = rexBuilder.makeLiteral( "def" );

        final RexLiteral literalZero = rexBuilder.makeExactLiteral( BigDecimal.ZERO );
        final RexLiteral literalOne = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral literalOneDotZero = rexBuilder.makeExactLiteral( new BigDecimal( 1.0 ) );

        // Check string comparison
        checkSimplify( eq( literalAbc, literalAbc ), "true" );
        checkSimplify( eq( literalAbc, literalDef ), "false" );
        checkSimplify( ne( literalAbc, literalAbc ), "false" );
        checkSimplify( ne( literalAbc, literalDef ), "true" );
        checkSimplify( gt( literalAbc, literalDef ), "false" );
        checkSimplify( gt( literalDef, literalAbc ), "true" );
        checkSimplify( gt( literalDef, literalDef ), "false" );
        checkSimplify( ge( literalAbc, literalDef ), "false" );
        checkSimplify( ge( literalDef, literalAbc ), "true" );
        checkSimplify( ge( literalDef, literalDef ), "true" );
        checkSimplify( lt( literalAbc, literalDef ), "true" );
        checkSimplify( lt( literalAbc, literalDef ), "true" );
        checkSimplify( lt( literalDef, literalDef ), "false" );
        checkSimplify( le( literalAbc, literalDef ), "true" );
        checkSimplify( le( literalDef, literalAbc ), "false" );
        checkSimplify( le( literalDef, literalDef ), "true" );

        // Check whole number comparison
        checkSimplify( eq( literalZero, literalOne ), "false" );
        checkSimplify( eq( literalOne, literalZero ), "false" );
        checkSimplify( ne( literalZero, literalOne ), "true" );
        checkSimplify( ne( literalOne, literalZero ), "true" );
        checkSimplify( gt( literalZero, literalOne ), "false" );
        checkSimplify( gt( literalOne, literalZero ), "true" );
        checkSimplify( gt( literalOne, literalOne ), "false" );
        checkSimplify( ge( literalZero, literalOne ), "false" );
        checkSimplify( ge( literalOne, literalZero ), "true" );
        checkSimplify( ge( literalOne, literalOne ), "true" );
        checkSimplify( lt( literalZero, literalOne ), "true" );
        checkSimplify( lt( literalOne, literalZero ), "false" );
        checkSimplify( lt( literalOne, literalOne ), "false" );
        checkSimplify( le( literalZero, literalOne ), "true" );
        checkSimplify( le( literalOne, literalZero ), "false" );
        checkSimplify( le( literalOne, literalOne ), "true" );

        // Check decimal equality comparison
        checkSimplify( eq( literalOne, literalOneDotZero ), "true" );
        checkSimplify( eq( literalOneDotZero, literalOne ), "true" );
        checkSimplify( ne( literalOne, literalOneDotZero ), "false" );
        checkSimplify( ne( literalOneDotZero, literalOne ), "false" );

        // Check different types shouldn't change simplification
        checkSimplifyUnchanged( eq( literalZero, literalAbc ) );
        checkSimplifyUnchanged( eq( literalAbc, literalZero ) );
        checkSimplifyUnchanged( ne( literalZero, literalAbc ) );
        checkSimplifyUnchanged( ne( literalAbc, literalZero ) );
        checkSimplifyUnchanged( gt( literalZero, literalAbc ) );
        checkSimplifyUnchanged( gt( literalAbc, literalZero ) );
        checkSimplifyUnchanged( ge( literalZero, literalAbc ) );
        checkSimplifyUnchanged( ge( literalAbc, literalZero ) );
        checkSimplifyUnchanged( lt( literalZero, literalAbc ) );
        checkSimplifyUnchanged( lt( literalAbc, literalZero ) );
        checkSimplifyUnchanged( le( literalZero, literalAbc ) );
        checkSimplifyUnchanged( le( literalAbc, literalZero ) );
    }


    @Test
    public void testSimpleDynamicVars() {
        assertTypeAndToString( vBool( 2 ), "?0:ROW.bool2", "BOOLEAN" );
        assertTypeAndToString( vBoolNotNull( 0 ), "?0:ROW.notNullBool0", "BOOLEAN NOT NULL" );

        assertTypeAndToString( vInt( 2 ), "?0:ROW.int2", "INTEGER" );
        assertTypeAndToString( vIntNotNull( 0 ), "?0:ROW.notNullInt0", "INTEGER NOT NULL" );

        assertTypeAndToString( vVarchar(), "?0:ROW.varchar0", "VARCHAR" );
        assertTypeAndToString( vVarcharNotNull( 9 ), "?0:ROW.notNullVarchar9", "VARCHAR NOT NULL" );
    }


    private void assertTypeAndToString( RexNode rexNode, String representation, String type ) {
        assertEquals( representation, rexNode.toString() );
        assertEquals( "type of " + rexNode, type, rexNode.getType().toString() + (rexNode.getType().isNullable() ? "" : " NOT NULL") );
    }


    @Test
    public void testIsDeterministic() {
        SqlOperator ndc = new SqlSpecialOperator( "NDC", Kind.OTHER_FUNCTION, 0, false, ReturnTypes.BOOLEAN, null, null ) {
            @Override
            public boolean isDeterministic() {
                return false;
            }
        };
        RexNode n = rexBuilder.makeCall( ndc );
        assertFalse( RexUtil.isDeterministic( n ) );
        assertEquals( 0, RexUtil.retainDeterministic( AlgOptUtil.conjunctions( n ) ).size() );
    }


    @Test
    public void testConstantMap() {
        final AlgDataType intType = typeFactory.createPolyType( PolyType.INTEGER );
        final AlgDataType rowType = typeFactory.builder()
                .add( "a", null, intType )
                .add( "b", null, intType )
                .add( "c", null, intType )
                .add( "d", null, intType )
                .add( "e", null, intType )
                .build();

        final RexDynamicParam range = rexBuilder.makeDynamicParam( rowType, 0 );
        final RexNode aRef = rexBuilder.makeFieldAccess( range, 0 );
        final RexNode bRef = rexBuilder.makeFieldAccess( range, 1 );
        final RexNode cRef = rexBuilder.makeFieldAccess( range, 2 );
        final RexNode dRef = rexBuilder.makeFieldAccess( range, 3 );
        final RexNode eRef = rexBuilder.makeFieldAccess( range, 4 );
        final RexLiteral literal1 = rexBuilder.makeExactLiteral( BigDecimal.ONE );
        final RexLiteral literal2 = rexBuilder.makeExactLiteral( BigDecimal.valueOf( 2 ) );

        final ImmutableMap<RexNode, RexNode> map =
                RexUtil.predicateConstants( RexNode.class, rexBuilder,
                        ImmutableList.of( eq( aRef, bRef ), eq( cRef, literal1 ), eq( cRef, aRef ),
                                eq( dRef, eRef ) ) );
        assertThat( getString( map ), is( "{1=?0:ROW.c, ?0:ROW.a=?0:ROW.b, ?0:ROW.b=?0:ROW.a, ?0:ROW.c=1, ?0:ROW.d=?0:ROW.e, ?0:ROW.e=?0:ROW.d}" ) );

        // Contradictory constraints yield no constants
        final RexNode ref0 = rexBuilder.makeInputRef( rowType, 0 );
        final ImmutableMap<RexNode, RexNode> map2 =
                RexUtil.predicateConstants( RexNode.class, rexBuilder,
                        ImmutableList.of(
                                eq( ref0, literal1 ),
                                eq( ref0, literal2 ) ) );
        assertThat( getString( map2 ), is( "{}" ) );

        // Contradictory constraints on field accesses SHOULD yield no constants but currently there's a bug
        final ImmutableMap<RexNode, RexNode> map3 =
                RexUtil.predicateConstants( RexNode.class, rexBuilder,
                        ImmutableList.of(
                                eq( aRef, literal1 ),
                                eq( aRef, literal2 ) ) );
        assertThat( getString( map3 ), is( "{1=?0:ROW.a, 2=?0:ROW.a}" ) );
    }


    @Test
    public void notDistinct() {
        checkSimplify( isFalse( isNotDistinctFrom( vBool( 0 ), vBool( 1 ) ) ), "IS DISTINCT FROM(?0:ROW.bool0, ?0:ROW.bool1)" );
    }


    /**
     * Unit test for "RexSimplify wrongly simplifies "COALESCE(+(NULL), x)" to "NULL"".
     */
    @Test
    public void testSimplifyCoalesce() {
        checkSimplify(
                coalesce( vIntNotNull(), vInt() ), // first arg not null
                "?0:ROW.notNullInt0" );
        checkSimplifyUnchanged( coalesce( vInt(), vIntNotNull() ) );
        checkSimplify(
                coalesce( vInt(), vInt() ), // repeated arg
                "?0:ROW.int0" );
        checkSimplify(
                coalesce( vIntNotNull(), vIntNotNull() ), // repeated arg
                "?0:ROW.notNullInt0" );
        checkSimplify(
                coalesce( vIntNotNull(), literal( 1 ) ),
                "?0:ROW.notNullInt0" );
        checkSimplifyUnchanged(
                coalesce( vInt(), literal( 1 ) ) );
        checkSimplify(
                coalesce( vInt(), plus( vInt(), vIntNotNull() ), literal( 1 ), vIntNotNull() ),
                "COALESCE(?0:ROW.int0, +(?0:ROW.int0, ?0:ROW.notNullInt0), 1)" );
        checkSimplify(
                coalesce( gt( nullInt, nullInt ), trueLiteral ),
                "true" );
        checkSimplify(
                coalesce( unaryPlus( nullInt ), unaryPlus( vInt() ) ),
                "+(?0:ROW.int0)" );
        checkSimplifyUnchanged( coalesce( unaryPlus( vInt( 1 ) ), unaryPlus( vInt() ) ) );

        checkSimplify( coalesce( nullInt, vInt() ), "?0:ROW.int0" );
        checkSimplify( coalesce( vInt(), nullInt, vInt( 1 ) ), "COALESCE(?0:ROW.int0, ?0:ROW.int1)" );
    }


    @Test
    public void simplifyNull() {
        checkSimplify3( nullBool, "null:BOOLEAN", "false", "true" );
        // null int must not be simplified to false
        checkSimplifyUnchanged( nullInt );
    }


    /**
     * Converts a map to a string, sorting on the string representation of its
     * keys.
     */
    private static String getString( ImmutableMap<RexNode, RexNode> map ) {
        final TreeMap<String, RexNode> map2 = new TreeMap<>();
        for ( Map.Entry<RexNode, RexNode> entry : map.entrySet() ) {
            map2.put( entry.getKey().toString(), entry.getValue() );
        }
        return map2.toString();
    }


    @Test
    public void testSimplifyFalse() {
        final AlgDataType booleanNullableType =
                typeFactory.createTypeWithNullability(
                        typeFactory.createPolyType( PolyType.BOOLEAN ), true );
        final RexNode booleanInput = input( booleanNullableType, 0 );
        final RexNode isFalse = isFalse( booleanInput );
        final RexCall result = (RexCall) simplify( isFalse );
        assertThat( result.getType().isNullable(), is( false ) );
        assertThat( result.getOperator(), is( OperatorRegistry.get( OperatorName.IS_FALSE ) ) );
        assertThat( result.getOperands().size(), is( 1 ) );
        assertThat( result.getOperands().get( 0 ), is( booleanInput ) );

        // Make sure that IS_FALSE(IS_FALSE(nullable boolean)) != IS_TRUE(nullable boolean)
        // IS_FALSE(IS_FALSE(null)) = IS_FALSE(false) = true
        // IS_TRUE(null) = false
        final RexNode isFalseIsFalse = isFalse( isFalse );
        final RexCall result2 = (RexCall) simplify( isFalseIsFalse );
        assertThat( result2.getType().isNullable(), is( false ) );
        assertThat( result2.getOperator(), is( OperatorRegistry.get( OperatorName.IS_NOT_FALSE ) ) );
        assertThat( result2.getOperands().size(), is( 1 ) );
        assertThat( result2.getOperands().get( 0 ), is( booleanInput ) );
    }


    @Test
    public void testSimplifyNot() {
        // "NOT(NOT(x))" => "x"
        checkSimplify( not( not( vBool() ) ), "?0:ROW.bool0" );
        // "NOT(true)"  => "false"
        checkSimplify( not( trueLiteral ), "false" );
        // "NOT(false)" => "true"
        checkSimplify( not( falseLiteral ), "true" );
        // "NOT(IS FALSE(x))" => "IS NOT FALSE(x)"
        checkSimplify( not( isFalse( vBool() ) ), "IS NOT FALSE(?0:ROW.bool0)" );
        // "NOT(IS TRUE(x))" => "IS NOT TRUE(x)"
        checkSimplify( not( isTrue( vBool() ) ), "IS NOT TRUE(?0:ROW.bool0)" );
        // "NOT(IS NULL(x))" => "IS NOT NULL(x)"
        checkSimplify( not( isNull( vBool() ) ), "IS NOT NULL(?0:ROW.bool0)" );
        // "NOT(IS NOT NULL(x)) => "IS NULL(x)"
        checkSimplify( not( isNotNull( vBool() ) ), "IS NULL(?0:ROW.bool0)" );
        // "NOT(AND(x0,x1))" => "OR(NOT(x0),NOT(x1))"
        checkSimplify( not( and( vBool( 0 ), vBool( 1 ) ) ), "OR(NOT(?0:ROW.bool0), NOT(?0:ROW.bool1))" );
        // "NOT(OR(x0,x1))" => "AND(NOT(x0),NOT(x1))"
        checkSimplify( not( or( vBool( 0 ), vBool( 1 ) ) ), "AND(NOT(?0:ROW.bool0), NOT(?0:ROW.bool1))" );
    }


    @Test
    public void testSimplifyAndNot() {
        // "x > 1 AND NOT (y > 2)" -> "x > 1 AND y <= 2"
        checkSimplify( and( gt( vInt( 1 ), literal( 1 ) ), not( gt( vInt( 2 ), literal( 2 ) ) ) ), "AND(>(?0:ROW.int1, 1), <=(?0:ROW.int2, 2))" );
        // "x = x AND NOT (y >= y)"
        //    -> "x = x AND y < y" (treating unknown as unknown)
        //    -> false (treating unknown as false)
        checkSimplify2( and( eq( vInt( 1 ), vInt( 1 ) ), not( ge( vInt( 2 ), vInt( 2 ) ) ) ), "AND(=(?0:ROW.int1, ?0:ROW.int1), <(?0:ROW.int2, ?0:ROW.int2))", "false" );

        // "NOT(x = x AND NOT (y = y))"
        //   -> "OR(x <> x, y >= y)" (treating unknown as unknown)
        //   -> "y IS NOT NULL" (treating unknown as false)
        checkSimplify2( not( and( eq( vInt( 1 ), vInt( 1 ) ), not( ge( vInt( 2 ), vInt( 2 ) ) ) ) ), "OR(<>(?0:ROW.int1, ?0:ROW.int1), >=(?0:ROW.int2, ?0:ROW.int2))", "IS NOT NULL(?0:ROW.int2)" );
    }


    @Test
    public void testSimplifyOrNot() {
        // "x > 1 OR NOT (y > 2)" -> "x > 1 OR y <= 2"
        checkSimplify( or( gt( vInt( 1 ), literal( 1 ) ), not( gt( vInt( 2 ), literal( 2 ) ) ) ), "OR(>(?0:ROW.int1, 1), <=(?0:ROW.int2, 2))" );

        // "x = x OR NOT (y >= y)"
        //    -> "x = x OR y < y" (treating unknown as unknown)
        //    -> "x IS NOT NULL" (treating unknown as false)
        checkSimplify2( or( eq( vInt( 1 ), vInt( 1 ) ), not( ge( vInt( 2 ), vInt( 2 ) ) ) ), "OR(=(?0:ROW.int1, ?0:ROW.int1), <(?0:ROW.int2, ?0:ROW.int2))", "IS NOT NULL(?0:ROW.int1)" );

        // "NOT(x = x OR NOT (y = y))"
        //   -> "AND(x <> x, y >= y)" (treating unknown as unknown)
        //   -> "FALSE" (treating unknown as false)
        checkSimplify2( not( or( eq( vInt( 1 ), vInt( 1 ) ), not( ge( vInt( 2 ), vInt( 2 ) ) ) ) ), "AND(<>(?0:ROW.int1, ?0:ROW.int1), >=(?0:ROW.int2, ?0:ROW.int2))", "false" );
    }


    private RexNode simplify( RexNode e ) {
        final RexSimplify simplify = new RexSimplify( rexBuilder, AlgOptPredicateList.EMPTY, RexUtil.EXECUTOR ).withParanoid( true );
        return simplify.simplifyUnknownAs( e, RexUnknownAs.UNKNOWN );
    }


    @Test
    public void testInterpreter() {
        assertThat( eval( trueLiteral ), is( true ) );
        assertThat( eval( nullInt ), is( NullSentinel.INSTANCE ) );
        assertThat( eval( eq( nullInt, nullInt ) ), is( NullSentinel.INSTANCE ) );
        assertThat( eval( eq( this.trueLiteral, nullInt ) ), is( NullSentinel.INSTANCE ) );
        assertThat( eval( eq( falseLiteral, trueLiteral ) ), is( false ) );
        assertThat( eval( ne( falseLiteral, trueLiteral ) ), is( true ) );
        assertThat( eval( ne( falseLiteral, nullInt ) ), is( NullSentinel.INSTANCE ) );
        assertThat( eval( and( this.trueLiteral, falseLiteral ) ), is( false ) );
    }


    /**
     * Unit tests for "RexCall#isAlwaysTrue returns incorrect result".
     */
    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNotNullisFalse() {
        // "((x IS NULL) IS NOT NULL) IS FALSE" -> false
        checkIs( isFalse( isNotNull( isNull( vBool() ) ) ), false );
    }


    @Test
    public void testIsAlwaysTrueAndFalseNotXisNullisNotNullisFalse() {
        // "(NOT ((x IS NULL) IS NOT NULL)) IS FALSE" -> true
        checkIs( isFalse( not( isNotNull( isNull( vBool() ) ) ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNotNullisTrue() {
        // "((x IS NULL) IS NOT NULL) IS TRUE" -> true
        checkIs( isTrue( isNotNull( isNull( vBool() ) ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseNotXisNullisNotNullisTrue() {
        // "(NOT ((x IS NULL) IS NOT NULL)) IS TRUE" -> false
        checkIs( isTrue( not( isNotNull( isNull( vBool() ) ) ) ), false );
    }


    @Test
    public void testIsAlwaysTrueAndFalseNotXisNullisNotNullisNotTrue() {
        // "(NOT ((x IS NULL) IS NOT NULL)) IS NOT TRUE" -> true
        checkIs( isNotTrue( not( isNotNull( isNull( vBool() ) ) ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNotNull() {
        // "(x IS NULL) IS NOT NULL" -> true
        checkIs( isNotNull( isNull( vBool() ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNotNullisNotNull() {
        // "(x IS NOT NULL) IS NOT NULL" -> true
        checkIs( isNotNull( isNotNull( vBool() ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNull() {
        // "(x IS NULL) IS NULL" -> false
        checkIs( isNull( isNull( vBool() ) ), false );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNotNullisNull() {
        // "(x IS NOT NULL) IS NULL" -> false
        checkIs( isNull( isNotNull( vBool() ) ), false );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNotNullisNotFalse() {
        // "((x IS NULL) IS NOT NULL) IS NOT FALSE" -> true
        checkIs( isNotFalse( isNotNull( isNull( vBool() ) ) ), true );
    }


    @Test
    public void testIsAlwaysTrueAndFalseXisNullisNotNullisNotTrue() {
        // "((x IS NULL) IS NOT NULL) IS NOT TRUE" -> false
        checkIs( isNotTrue( isNotNull( isNull( vBool() ) ) ), false );
    }


    /**
     * Checks that {@link RexNode#isAlwaysTrue()}, {@link RexNode#isAlwaysTrue()} and {@link RexSimplify} agree that an expression reduces to true or false.
     */
    private void checkIs( RexNode e, boolean expected ) {
        assertThat( "isAlwaysTrue() of expression: " + e.toString(), e.isAlwaysTrue(), is( expected ) );
        assertThat( "isAlwaysFalse() of expression: " + e.toString(), e.isAlwaysFalse(), is( !expected ) );
        assertThat( "Simplification is not using isAlwaysX informations", simplify( e ).toString(), is( expected ? "true" : "false" ) );
    }


    private Comparable eval( RexNode e ) {
        return RexInterpreter.evaluate( e, ImmutableMap.of() );
    }

}

