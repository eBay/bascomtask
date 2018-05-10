/************************************************************************
Copyright 2018 eBay Inc.
Author/Developer: Brendan McCarthy
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    https://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**************************************************************************/
package com.ebay.bascomtask.main;

import org.junit.Test;

import com.ebay.bascomtask.annotations.Work;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests lots of tasks
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS")
public class TaskVolumeTest extends PathTaskTestBase {
	
	@Test
	public void test50TasksBy10Deep() {
		class Root1 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root2 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root3 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root4 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root5 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root6 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root7 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root8 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root9 extends PathTask {
			@Work public void exec() {got();}
		}
		class Root10 extends PathTask {
			@Work public void exec() {got();}
		}
		PathTask r1 = track.work(new Root1());
		PathTask r2 = track.work(new Root2());
		PathTask r3 = track.work(new Root3());
		PathTask r4 = track.work(new Root4());
		PathTask r5 = track.work(new Root5());
		PathTask r6 = track.work(new Root6());
		PathTask r7 = track.work(new Root7());
		PathTask r8 = track.work(new Root8());
		PathTask r9 = track.work(new Root9());
		PathTask r10 = track.work(new Root10());
		
		
		class A1 extends PathTask {
			@Work public void exec(Root1 r1) {got(r1);}
		}
		class A2 extends PathTask {
			@Work public void exec(Root1 r1, Root3 r3) {got(r1,r3);}
		}
		class A3 extends PathTask {
			@Work public void exec(Root3 r3, Root4 r4, Root7 r7, Root8 r8, Root9 r9) {got(r3,r4,r7,r8,r9);}
		}
		class A4 extends PathTask {
			@Work public void exec(Root5 r5, Root6 r6, Root7 r7) {got(r5,r6,r7);}
		}
		class A5 extends PathTask {
			@Work public void exec(Root1 r1, Root2 r2, Root4 r4, Root8 r8, Root10 r10) {got(r1,r2,r4,r8,r10);}
		}
		PathTask a1 = track.work(new A1()).exp(r1);
		PathTask a2 = track.work(new A2()).exp(r1,r3);
		PathTask a3 = track.work(new A3()).exp(r3,r4,r7,r8,r9);
		PathTask a4 = track.work(new A4()).exp(r5,r6,r7);
		PathTask a5 = track.work(new A5()).exp(r1,r2,r4,r8,r10);
		
				
		class B1 extends PathTask {
			@Work public void exec(A3 a3, A5 a5) {got(a3,a5);}
		}
		class B2 extends PathTask {
			@Work public void exec(A5 a5) {got(a5);}
		}
		class B3 extends PathTask {
			@Work public void exec(A1 a1, A2 a2, A3 a3, A4 a4, A5 a5) {got(a1,a2,a3,a4,a5);}
		}
		class B4 extends PathTask {
			@Work public void exec(Root7 r7, A3 a3) {got(r7,a3);}
		}
		class B5 extends PathTask {
			@Work public void exec(Root7 r7, A3 a3, Root1 r1, A1 a1) {got(r7,a3,r1,a1);}
		}
		class B6 extends PathTask {
			@Work public void exec(A2 a2, Root10 r10, A1 a1, Root9 r9, Root8 r8) {got(a2,r10,a1,r9,r8);}
		}
		class B7 extends PathTask {
			@Work public void exec(Root5 r5, Root7 r7, A2 a2, A3 a3, A4 a4, Root6 r6, Root2 r2) {got(r5,r7,a2,a3,a4,r6,r2);}
		}
		PathTask b1 = track.work(new B1()).exp(a3,a5);
		PathTask b2 = track.work(new B2()).exp(a5);
		PathTask b3 = track.work(new B3()).exp(a1,a2,a3,a4,a5);
		PathTask b4 = track.work(new B4()).exp(r7,a3);
		PathTask b5 = track.work(new B5()).exp(r7,a3,r1,a1);
		PathTask b6 = track.work(new B6()).exp(a2,r10,a1,r9,r8);
		PathTask b7 = track.work(new B7()).exp(r5,r7,a2,a3,a4,r6,r2);

		class C1 extends PathTask {
			@Work public void exec(A3 a3, A5 a5) {got(a3,a5);}
		}
		class C2 extends PathTask {
			@Work public void exec(Root9 r9, A4 a4, B5 b5) {got(r9,a4,b5);}
		}
		class C3 extends PathTask {
			@Work public void exec(B1 b1) {got(b1);}
		}
		class C4 extends PathTask {
			@Work public void exec(B1 b1, B4 b4, A3 a3, Root6 r6) {got(b1,b4,a3,r6);}
		}
		class C5 extends PathTask {
			@Work public void exec(Root1 r1, B2 b2, B7 b7, A3 a3, B4 b4) {got(r1,b2,b7,a3,b4);}
		}
		PathTask c1 = track.work(new C1()).exp(a3,a5);
		PathTask c2 = track.work(new C2()).exp(r9,a4,b5);
		PathTask c3 = track.work(new C3()).exp(b1);
		PathTask c4 = track.work(new C4()).exp(b1,b4,a3,r6);
		PathTask c5 = track.work(new C5()).exp(r1,b2,b7,a3,b4);

		class D1 extends PathTask {
			@Work public void exec(C3 c3, A4 a4) {got(c3,a4);}
		}
		class D2 extends PathTask {
			@Work public void exec(Root10 r10, Root8 r8) {got(r10,r8);}
		}
		class D3 extends PathTask {
			@Work public void exec(B4 b4, B5 b5) {got(b4,b5);}
		}
		class D4 extends PathTask {
			@Work public void exec(C2 c2, C3 c3, C4 c4, C5 c5) {got(c2,c3,c4,c5);}
		}
		class D5 extends PathTask {
			@Work public void exec(C1 c1, B7 b7) {got(c1,b7);}
		}
		final PathTask d1 = track.work(new D1()).exp(c3,a4);
		final PathTask d2 = track.work(new D2()).exp(r10,r8);
		final PathTask d3 = track.work(new D3()).exp(b4,b5);
		final PathTask d4 = track.work(new D4()).exp(c2,c3,c4,c5);
		final PathTask d5 = track.work(new D5()).exp(c1,b7);
		
		class E1 extends PathTask {
			@Work public void exec(D3 c3, A4 a4) {got(d3,a4);}
		}
		class E2 extends PathTask {
			@Work public void exec(Root10 r10, D4 d4, D5 d5, Root8 r8) {got(r10,d4,d5,r8);}
		}
		class E3 extends PathTask {
			@Work public void exec(D3 d3) {got(d3);}
		}
		class E4 extends PathTask {
			@Work public void exec(Root1 r1, C3 c3, C4 c4, C5 c5, A5 a5) {got(r1,c3,c4,c5,a5);}
		}
		class E5 extends PathTask {
			@Work public void exec(C1 c1, B7 b7) {got(c1,b7);}
		}
		class E6 extends PathTask {
			@Work public void exec(C1 c1, B7 b7) {got(c1,b7);}
		}
		class E7 extends PathTask {
			@Work public void exec(B1 b1, A1 a1) {got(b1,a1);}
		}
		class E8 extends PathTask {
			@Work public void exec(A2 a2, C2 c2, B6 b6) {got(a2,c2,b6);}
		}
		PathTask e1 = track.work(new E1()).exp(d3,a4);
		PathTask e2 = track.work(new E2()).exp(r10,d4,d5,r8);
		PathTask e3 = track.work(new E3()).exp(d3);
		PathTask e4 = track.work(new E4()).exp(r1,c3,c4,c5,a5);
		PathTask e5 = track.work(new E5()).exp(c1,b7);
		PathTask e6 = track.work(new E6()).exp(c1,b7);
		PathTask e7 = track.work(new E7()).exp(b1,a1);
		PathTask e8 = track.work(new E8()).exp(a2,c2,b6);
		
		class F1 extends PathTask {
			@Work public void exec(E8 e8) {got(e8);}
		}
		class F2 extends PathTask {
			@Work public void exec(E8 e8,A2 a2) {got(e8,a2);}
		}
		PathTask f1 = track.work(new F1()).exp(e8);
		PathTask f2 = track.work(new F2()).exp(e8,a2);
		
		class G1 extends PathTask {
			@Work public void exec(F2 f2, E5 e5, C3 c3) {got(f2,e5,c3);}
		}
		class G2 extends PathTask {
			@Work public void exec(A1 a1, B2 b2, C3 c3, D4 d4, E5 e5, F1 f1) {got(a1,b2,c3,d4,e5,f1);}
		}
		PathTask g1 = track.work(new G1()).exp(f2,e5,c3);
		PathTask g2 = track.work(new G2()).exp(a1,b2,c3,d4,e5,f1);
		
		class H1 extends PathTask {
			@Work public void exec(G2 g2, Root10 r10) {got(g2,r10);}
		}
		class H2 extends PathTask {
			@Work public void exec(A1 a1, B2 b2, C3 c3, D4 d4, E5 e5, F1 f1, G2 g2) {got(a1,b2,c3,d4,e5,f1,g2);}
		}
		PathTask h1 = track.work(new H1()).exp(g2,r10);
		PathTask h2 = track.work(new H2()).exp(a1,b2,c3,d4,e5,f1,g2);
		
		class I1 extends PathTask {
			@Work public void exec(F2 f2, E5 e5, C3 c3) {got(f2,e5,c3);}
		}
		class I2 extends PathTask {
			@Work public void exec(H1 h1, B2 b2, G2 g2, D4 d4, E5 e5, F1 f1) {got(h1,b2,g2,d4,e5,f1);}
		}
		PathTask i1 = track.work(new I1()).exp(f2,e5,c3);
		PathTask i2 = track.work(new I2()).exp(h1,b2,g2,d4,e5,f1);
		
		class J1 extends PathTask {
			@Work public void exec(I1 i1, I2 i2) {got(i1,i2);}
		}
		class J2 extends PathTask {
			@Work public void exec(C3 c3, E5 e5, F2 f2, I2 i2) {got(c3,e5,f2,i2);}
		}
		PathTask j1 = track.work(new J1()).exp(i1,i2);
		PathTask j2 = track.work(new J2()).exp(c3,e5,f2,i2);
		
		verify(0,30);
	}
}


