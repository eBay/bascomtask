package com.ebay.bascomtask.main;


import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.main.Orchestrator;

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
			Top(int v) {this.v = v;}
			@Work public void exec() {} // Not even really needed
		}
		class Left {
			private final int v;
			int out;
			Left(int v) {this.v = v;}
			@Work public void exec(Top top) {out = top.v + v;}
		}
		class Right {
			private final int v;
			int out;
			Right(int v) {this.v = v;}
			@Work public void exec(Top top) {out = top.v + v;}
		}
		class Bottom {
			int out;
			@Work public void exec(Left b, Right c) {
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


