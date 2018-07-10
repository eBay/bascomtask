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

import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.main.Orchestrator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS") // Simplistic
                                                                 // rule misses
                                                                 // valid usage
public class LoadTest {

    public static void main(String[] args) {
        Loader.run(3,100,new Loader.Runner() {
            @Override
            public void run() throws Exception {
                int r = loadDiamond();
                if (r != 117) {
                    throw new RuntimeException("Bad return result " + r);
                }
            }
        });
        BascomConfigFactory.getConfig().notifyTerminate();
    }

    static int loadDiamond() {
        class Top {
            private final int v;

            Top(int v) {
                this.v = v;
            }

            @Work
            public void exec() {
            } // Not even really needed
        }
        class Left {
            private final int v;
            int out;

            Left(int v) {
                this.v = v;
            }

            @Work
            public void exec(Top top) {
                out = top.v + v;
            }
        }
        class Right {
            private final int v;
            int out;

            Right(int v) {
                this.v = v;
            }

            @Work
            public void exec(Top top) {
                out = top.v + v;
            }
        }
        class Bottom {
            int out;

            @Work
            public void exec(Left b, Right c) {
                out = b.out + c.out;
            }
        }
        Orchestrator orc = Orchestrator.create();
        orc.addWork(new Top(5));
        orc.addWork(new Left(7));
        orc.addWork(new Right(100));
        Bottom bottom = new Bottom();
        orc.addWork(bottom);
        orc.execute(999999);
        return bottom.out;
    }

}
