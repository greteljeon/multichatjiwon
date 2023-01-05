package selector;

import java.nio.ByteBuffer;

public class ReadResult {
    public enum Result
    {
    	UNDERFLOW, REGISTER, TEXT, FILE
    }
    
    private ReadResult(Result result) {
    	this.result = result;
    }
    
    private final Result result;
    private int demand;
    private ByteBuffer textBuffer;
    
    public static ReadResult underflow(int demand)
    {
    	ReadResult readResult = new ReadResult(Result.UNDERFLOW);
    	readResult.setDemand(demand);
    	return readResult;
    }
    
    public static ReadResult register()
    {
    	ReadResult readResult = new ReadResult(Result.REGISTER);
    	return readResult;
    }
    
    public static ReadResult text(ByteBuffer textBuffer)
    {
    	ReadResult readResult = new ReadResult(Result.TEXT);
    	readResult.setTextBuffer(textBuffer);
    	return readResult;
    }
    public static ReadResult file(ByteBuffer textBuffer)	
    {
    	ReadResult readResult = new ReadResult(Result.FILE);
    	readResult.setTextBuffer(textBuffer);
		return readResult;
    }
    
    public Result getResult()
    {
    	return result;
    }
    
    public void setDemand(int demand)
    {
    	this.demand = demand;
    }
    
    public int getDemand()
    {
    	return demand;
    }
    
    public void setTextBuffer(ByteBuffer textBuffer)
    {
    	this.textBuffer = textBuffer;
    }
    
    public ByteBuffer getTextBuffer()
    {
    	return textBuffer;
    }
}
