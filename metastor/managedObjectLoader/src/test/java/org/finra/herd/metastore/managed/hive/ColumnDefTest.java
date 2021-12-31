/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
package org.finra.herd.metastore.managed.hive;

import org.finra.herd.metastore.managed.format.ColumnDef;
import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ColumnDefTest {
	@Test
	public void decimalColTest() {
		ColumnDef def1 = ColumnDef.builder().type( "DECIMAL" ).build();
		ColumnDef def2 = ColumnDef.builder().type( "DECIMAL" ).build();

		assertThat( "DECIMAL=DECIMAL", def1.isSameType( def2 ) );
		def2.setType( ("DECIMAL(10)") );
		assertThat( "DECIMAL=DECIMAL(10)", def1.isSameType( def2 ) );

		def2.setType( ("DECIMAL(10, 0)") );
		assertThat( "DECIMAL=DECIMAL(10, 0)", def1.isSameType( def2 ) );

		def1.setType( "DECIMAL(18,8)" );
		assertThat( "DECIMAL(18,0) != DECIMAL(10, 0)", !def1.isSameType( def2 ) );

		def2.setType( ("DECIMAL(18, 8)") );
		assertThat( "DECIMAL(18,0) != DECIMAL(18, 8)", def1.isSameType( def2 ) );
	}

	@Test
	public void testIsSameTypeDecimal() {
		ColumnDef d1 = ColumnDef.builder().index( 0 ).name( "n1" ).type( "decimal(18,0)" ).build();
		ColumnDef d2 = ColumnDef.builder().index( 0 ).name( "n1" ).type( "DECIMAL(18)" ).build();

		assertTrue( d1.isSameType( d2 ) );

		d2 = ColumnDef.builder().index( 0 ).name( "n1" ).type( "DECIMAL(19,0)" ).build();

		assertFalse( d1.isSameType( d2 ) );

		d1.setType( "DECIMAL" );
		d2.setType( "DECIMAL(10,0)" );

		assertTrue( d1.isSameType( d2 ) );
		assertTrue( d2.isSameType( d1 ) );
	}
}
