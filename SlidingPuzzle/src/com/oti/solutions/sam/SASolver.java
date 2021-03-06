package com.oti.solutions.sam;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

import com.oti.Board;
import com.oti.CostFunction;
import com.oti.Location;
import com.oti.Move;
import com.oti.Piece;
import com.oti.PuzzleException;
import com.oti.Solution;
import com.oti.Solver;

public class SASolver extends Solver {
	static class Evalue {
		Evalue parent = null;
		BigInteger state=null; // code for the current board
		byte prevAction; // code for the move swap(i,j), i<j, i and j between [0, 15], 4bit = i, 4bits=j 
		public Evalue() {}
	}
	static class FrontierLeaf {
		int value; // order value 
		int cost; // nb steps from start
		BigInteger state=null; // current board
		byte prevAction; // "this" results from prevAction
		Evalue previous=null; // "this" results from board and previous path
		public FrontierLeaf() {}
	}
	static class LeafComp implements Comparator {
		public LeafComp() {}

		@Override
		public int compare(Object o1, Object o2) {
			FrontierLeaf f1 = (FrontierLeaf)o1;
			FrontierLeaf f2 = (FrontierLeaf)o2;
			return (f1.value <f2.value) ? -1 : ((f1.value ==f2.value)? 0 : 1 );
		}
	}	
	static interface Heuristic {
		public int distanceToArrival(BigInteger arrival);
	}
	
	static class HeuristicByPermutation implements Heuristic{
		byte[] m_arrival;
		public HeuristicByPermutation(BigInteger arrival) {
			m_arrival = arrival.toByteArray();			
		}

		/**
		 * Heuristic based on the number of direct permutation between state and arrival
		 * */
		public int distanceToArrival(BigInteger state) {
			byte[] bstate = state.toByteArray();
			int differences = 0;
			int diff = 0;
			for (int i = 0; i < bstate.length; i++) {
				diff = m_arrival[i]-bstate[i];
				if (diff !=0) {
					differences += (0x0F & diff)!=0 ? 1 : 0; 					
					differences += (0x0F & (diff >> 4))!=0 ? 1 : 0; 
				}
			}					
			return differences/2;
		}
	}		
	
	BigInteger arrivalState;
	public SASolver(CostFunction costFunction) {
		super(costFunction);
		
	}

	public SASolver() {
		super(null);
	}
	
	Heuristic m_hFunc=null;
	@Override
	protected Solution solve(Board board, boolean showChanges) throws PuzzleException, CloneNotSupportedException {
		HashMap<BigInteger, Evalue> visitedNodes = new HashMap<BigInteger, Evalue>();
		PriorityQueue<FrontierLeaf> frontierSet = new PriorityQueue<FrontierLeaf>(100, new LeafComp());
				
		arrivalState = buildArrivalState(); 
		/*        System.out.println(" SAM TO REMOVE fake arrival state");
		board.makeMove(new Move(Piece.pieceForNumber(15), Location.locationFor(3, 3, 4)), true);
		board.makeMove(new Move(Piece.pieceForNumber(11), Location.locationFor(2, 3, 4)), true);
		board.makeMove(new Move(Piece.pieceForNumber(7), Location.locationFor(2, 2, 4)), true);
		board.makeMove(new Move(Piece.pieceForNumber(6), Location.locationFor(2, 1, 4)), true);*/
        
		m_hFunc = new HeuristicByPermutation(arrivalState);
		FrontierLeaf currLeaf = buildInitialLeaf(board);
		Evalue currEvalue = createEvalue(currLeaf);
		// store evalue for fast retrieval
		visitedNodes.put(currEvalue.state, currEvalue);

		
		if (showChanges) {
			System.out.println(" Dump of initial Board");
			board.describeBoard();
			System.out.println(" Dump of initialState");
			printState(currLeaf.state);
			System.out.println(" Dump of arrivalState");
			printState(arrivalState);
			
			/* test serialization of action byte + move on a BigInt board
			int firstCell = 7; 
			int otherCell = 11;						
			byte action = buildAction( firstCell, otherCell);
			System.out.println(" buildAction ("+firstCell+","+otherCell+") = "+action);
			firstCell = 0x0F & (action >> 4); 
			otherCell = 0x0F & action;			
			System.out.println(" from action firstCell= "+firstCell);
			System.out.println(" from action otherCell= "+otherCell);
			
			BigInteger newState = moveState(currLeaf.state, action);
			System.out.println(" Dump before move ");
			printState(currLeaf.state);
			System.out.println(" Dump of moved state");
			printState(newState);
			*/
		}
		
		
		// propagation loop
		int counter = 0;
		while(!currEvalue.state.equals(arrivalState)) {
			++counter;
			
			if (counter%1000==0) {
				System.out.println(" propagation loop :"+ counter+" cost :"+ currLeaf.cost+" value :"+ currLeaf.value);
				
			}

			// explore moves from current evalue
			int zeroCellIndex = getZeroCellIndex(currEvalue.state);
			if ((zeroCellIndex%4) +1 < 4) { // move empty cell to the right
				int otherCellIndex = zeroCellIndex+1;
				byte action = buildAction(zeroCellIndex, otherCellIndex);
				// test backtrack
				if (action!=currEvalue.prevAction) {
					BigInteger newState = moveState(currEvalue.state, action);
					// test for previous visit
					if (!visitedNodes.containsKey(newState)) {
						// create new leaf
						createNewLeaf(frontierSet, currLeaf, currEvalue, action, newState);
					}
				}
			} 
			if ((zeroCellIndex%4) -1 >= 0) { // move empty cell to the left
				int otherCellIndex = zeroCellIndex-1; 
				byte action = buildAction(otherCellIndex, zeroCellIndex);
				// test backtrack
				if (action!=currEvalue.prevAction) {
					BigInteger newState = moveState(currEvalue.state, action);
					// test for previous visit
					if (!visitedNodes.containsKey(newState)) {
						// create new leaf
						createNewLeaf(frontierSet, currLeaf, currEvalue, action, newState);
					}
				}				
			}
			if ((zeroCellIndex+4) < 16) { // move empty cell down
				int otherCellIndex = zeroCellIndex+4; 
				byte action = buildAction(zeroCellIndex, otherCellIndex);
				// test backtrack
				if (action!=currEvalue.prevAction) {
					BigInteger newState = moveState(currEvalue.state, action);
					// test for previous visit
					if (!visitedNodes.containsKey(newState)) {
						// create new leaf
						createNewLeaf(frontierSet, currLeaf, currEvalue, action, newState);
					}
				}				
			}
			if ((zeroCellIndex-4) >= 0) { // move empty cell up
				int otherCellIndex = zeroCellIndex-4; 
				byte action = buildAction(otherCellIndex, zeroCellIndex);
				// test backtrack
				if (action!=currEvalue.prevAction) {
					BigInteger newState = moveState(currEvalue.state, action);
					// test for previous visit
					if (!visitedNodes.containsKey(newState)) {
						// create new leaf
						createNewLeaf(frontierSet, currLeaf, currEvalue, action, newState);
					}
				}				
			}
 
			
			if (frontierSet.isEmpty()) {
				throw new PuzzleException("no path possible - NO LEAF LEFT");
			}
			// extract next best leaf			
			currLeaf = frontierSet.poll();			
			// build evaluation from leaf
			currEvalue = createEvalue(currLeaf);
			// store evalue for fast retrieval
			visitedNodes.put(currEvalue.state, currEvalue);
		}
		
		return buildPath(currEvalue); 
	}

	/**
	 * Transfrom the current state in a new state resulting from the swap of cells coded by action.
	 * 
	 * */
	private BigInteger moveState(BigInteger state, byte action) {
		byte[] pieces = state.toByteArray();		
		int firstCell = 0x0F & (action >> 4); 
		int otherCell = 0x0F & action;
		
		int firstCellVal =0;
		firstCellVal = pieces[firstCell/2];
		if (firstCell%2 == 0) {
			firstCellVal = firstCellVal >> 4;
		} else {
			firstCellVal = firstCellVal & 0x0F;			
		}
		int otherCellVal = pieces[otherCell/2];
		if (otherCell%2 == 0) {
			otherCellVal = otherCellVal >> 4;
		} else {
			otherCellVal = otherCellVal & 0x0F;			
		}

		// swap othercell with firstcell value
		if (otherCell%2 == 0) {
			pieces[otherCell/2] = (byte) ((0x0F & pieces[otherCell/2]) | (firstCellVal << 4));
		} else {
			pieces[otherCell/2] = (byte) ((0xF0 & pieces[otherCell/2]) | (firstCellVal & 0x0F));
		}
		
		// swap firstcell with othercell value
		if (firstCell%2 == 0) {
			pieces[firstCell/2] = (byte) ((0x0F & pieces[firstCell/2]) | (otherCellVal << 4));
		} else {
			pieces[firstCell/2] = (byte) ((0xF0 & pieces[firstCell/2]) | (otherCellVal & 0x0F));
		}		
		return new BigInteger(pieces);
	}

	/**
	 * Construct a byte representing the action 
	 * high order 4bits = firstCellIndex
	 * low order 4bits = otherCellIndex
	 * 
	 * @param firstCellIndex < otherCellIndex for comaprison between action bytes. 
	 * @param otherCellIndex
	 * @return the action byte coding the move swap(firstCellIndex, otherCellIndex)
	 */
	private byte buildAction(int firstCellIndex , int otherCellIndex) {
		assert(firstCellIndex<otherCellIndex);
		int action = (0x0F & firstCellIndex);
		action = action << 4;
		action |= (0x0F & otherCellIndex);
		return (byte) action;
	}

	/**
	 *  find the cell with zero from the current state
	 * @param state
	 * @return index from 0 to 15 of the empty cell
	 * @throws PuzzleException 
	 */
	private int getZeroCellIndex(BigInteger state) throws PuzzleException {
		byte[] pieces = state.toByteArray();
		
		for (int i = 0; i < pieces.length; i++) {
			if ( (pieces[i] & 0xF0) == 0)
				return 2*i;
						
			else if ( (pieces[i] & 0x0F) == 0)
				return 2*i +1;
		}		

		throw new PuzzleException("getZeroCellIndex() empty cell not found");
	}

	// create a new leaf from the current node and add it to the frontier
	private void createNewLeaf(PriorityQueue<FrontierLeaf> frontierSet, FrontierLeaf currLeaf, Evalue currEvalue, byte action, BigInteger newState) {
		FrontierLeaf newLeaf = new FrontierLeaf();
		newLeaf.cost = currLeaf.cost + 1;
		newLeaf.value = newLeaf.cost + m_hFunc.distanceToArrival(newState);
		newLeaf.state= newState;
		newLeaf.prevAction = action;
		newLeaf.previous=currEvalue;						
		// insert leaf in frontier
		frontierSet.add(newLeaf);
	}


	/** build a list of Move fromt start to arrival, from the chain of Evalue*/
	private Solution buildPath(Evalue currEvalue) {
		// list from end to beginning
		ArrayList<Move> revMoves = new ArrayList<Move>();
		// check for departure == arrival
		if (currEvalue.parent!=null) {
			while(currEvalue.parent!=null) {				
				revMoves.add( createMove(currEvalue.parent.state, currEvalue.prevAction));
				currEvalue = currEvalue.parent;
			}
		}
		// reverse the list
		Collections.reverse(revMoves);
		Solution sol = new Solution();
		for (Move move : revMoves) {
//	        Piece pieceToMove = move.getPiece();
//	        Location nextLocation = move.getNextLocation();
//			System.out.println();
//            System.out.printf("\tcom.oti.Move: %s to %s\n", pieceToMove, nextLocation);
            
			sol.add( move);
		} 
		
		
		return sol;
	}

	/**
	 * create a Move object from an action byte.
	 **/
	private Move createMove(BigInteger state, byte action) {
		// 
		byte[] pieces = state.toByteArray();		
		int firstCell = 0x0F & (action >> 4); 
		int otherCell = 0x0F & action;

//		System.out.println("SAM TO REMOVE createMove from state ");
//		printState(state);
				
		byte firstCellVal =0;
		firstCellVal = pieces[firstCell/2];
		if (firstCell%2 == 0) {
			firstCellVal = (byte)((firstCellVal  & 0xF0)>>4);
		} else {
			firstCellVal = (byte)(firstCellVal & 0x0F);			
		}
		byte otherCellVal = pieces[otherCell/2];
		if (otherCell%2 == 0) {
			otherCellVal = (byte) ((otherCellVal & 0xF0)>>4);
		} else {
			otherCellVal = (byte) (otherCellVal & 0x0F);			
		}

		//		System.out.println("SAM TO REMOVE createMove firstCel "+ firstCell+" otherCell "+ otherCell);
		//		System.out.println("SAM TO REMOVE createMove firstCellVal "+ firstCellVal+" otherCellVal "+ otherCellVal);
		
		
		Piece currPiece = null;
		Location  currLoc = null;
		if (firstCellVal==0) {
			currPiece = Piece.pieceForNumber(otherCellVal);
			currLoc = Location.locationFor(firstCell%4, firstCell/4, 4);
		} else {
			assert(otherCellVal==0);
			currPiece = Piece.pieceForNumber(firstCellVal);
			currLoc = Location.locationFor(otherCell%4, otherCell/4, 4);
			
		}
		//		System.out.println("SAM TO REMOVE createMove currPiece "+ currPiece+" currLoc "+ currLoc);
		
		return new Move(currPiece, currLoc);
	}

	// leaf is just out from frontier => create evalue for storing the visited node 
	// link evalue with previous path
	private Evalue createEvalue(FrontierLeaf currLeaf) {
		Evalue res = new Evalue();
		res.parent = currLeaf.previous;
		res.state=currLeaf.state; 
		res.prevAction =  currLeaf.prevAction;
		return res;
	}

	// create the first leaf corresponding to the initial state
	private FrontierLeaf buildInitialLeaf(Board board) {
		FrontierLeaf res = new FrontierLeaf();  
		res.cost = 0;
		res.state= createState(board); // current board
	    res.prevAction =0; // no previous action for leading to the initial state, bit 6 true
	    res.previous=null;		
		return res;
	}

	/**
	 * shrink a Board object in a BigInt each 4 bits representing one call of the board.
	 * */
	private BigInteger createState(Board board) {
		BigInteger res = BigInteger.valueOf(0);
		for (int i = 0; i < 4; i++) {
			int fourth = 0;
			for (int j = 0; j < 4; j++) {				
				Piece cell = board.pieceAt(j, i);
				fourth = fourth << 4;
				fourth |= cell.getPieceNumber();
			}
			res= res.shiftLeft(16);
			res = res.add(BigInteger.valueOf(fourth));
		}
		
		
		
		return res;
	}

	// helper func
	private void printState(BigInteger state) {
		byte[] pieces = state.toByteArray();
		
		for (int i = 0; i < pieces.length; i++) {
			if (i%2==0)
				System.out.print(" ");		
			System.out.print((int)((pieces[i] & 0xF0)>>4 ));			
			System.out.print(" ");		
			System.out.print((int)(pieces[i] & 0x0F) );			
			System.out.print(" ");			
		}

		System.out.println();
	}


	// create the state (BigInterger) representing the target [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0] 
	private BigInteger buildArrivalState() {
		long part1 =0;
		for (int i=1; i<=8; ++i) {
			part1 |= i;
			if (i<8)
				part1 = part1 << 4;
		}
		BigInteger sol = BigInteger.valueOf(part1);
		
		part1 =0L;
		for (int i=9; i<16; ++i) {
			part1 |= i;
			part1 = part1 << 4;
		}
		sol = sol.shiftLeft(32);

		BigInteger part2 = BigInteger.valueOf(part1);
		sol = sol.add( part2);
		return sol;
	}

}
