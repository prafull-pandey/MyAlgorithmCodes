package com.pandey.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ReadAndExecuteScript {
	private String scriptFilePath="";
	private File scriptFile;
	private Connection connection;
	private char lineTerminator=';';
	private char stringQuote='\'';
	private int maxBatchSize=10;
	ReadAndExecuteScript(String filePath){
		this.scriptFilePath=filePath;
		scriptFile=new File(this.scriptFilePath);

	}
	boolean createConnection() {
		try {
			Class.forName("org.postgresql.Driver");
			connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres");
			connection.setAutoCommit(false);  
		} catch (ClassNotFoundException|SQLException e) {
			e.printStackTrace();
			return false;
		} 
		return true;

	}
	void readScriptFileAndExecute(){
		Statement stmt=null;
		try {
			BufferedReader reader=new BufferedReader(new FileReader(scriptFile));
			LineNumberReader lnReader=new LineNumberReader(reader);
			List<String> queriesList=new ArrayList<>();
			String line;
			stmt=connection.createStatement();
			StringBuilder queryString=new StringBuilder("");
			int batchSize=0;
			int lineNumber=0;
			int currentQueryQuotesCount=0;
			while((line=reader.readLine())!=null) {
				lineNumber=lnReader.getLineNumber();
				line=line.trim();
				if(line.length()==0 || line.startsWith("--")|| line.startsWith("//")) {
					continue;
				}
				int curIndex=0;
				if(line.indexOf(lineTerminator)==-1){
					queryString.append(line.substring(curIndex)).append(" ");
					currentQueryQuotesCount+=queryString.chars().filter(num -> num == stringQuote).count();
				}else {
					boolean isterminate=true;
					while(isterminate) {
						int terminatePos=findTerminatePosition(line, currentQueryQuotesCount,curIndex);
						isterminate=terminatePos>=0;//check if applicable for ; on last
						if(isterminate) {
							queryString.append(line.substring(curIndex,terminatePos));
							curIndex=terminatePos+1;
							System.out.println(queryString.toString());
							//stmt.execute(queryString.toString());
							stmt.addBatch(queryString.toString());
							queriesList.add(queryString.toString());
							batchSize++;
							queryString.setLength(0);
							if(batchSize>=maxBatchSize) {
								executeAllBatch(stmt,queriesList);
								batchSize=0;
								queriesList.clear();
							}
						}else {
							if(curIndex<line.length()) {
								queryString.append(line.substring(curIndex)).append(" ");
								currentQueryQuotesCount+=queryString.chars().filter(num -> num == stringQuote).count();
							}
						}

					}

				}
			}
			if(batchSize>0) {
				executeAllBatch(stmt,queriesList);
				batchSize=0;
				queriesList.clear();
			}
		}catch(IOException | SQLException e) {
			e.printStackTrace();
		}
		finally {
			try {
				stmt.close();
				connection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	private int findTerminatePosition(String currentLine, int quoteCounts, int currentIndex) {
		int terminatePos=currentLine.indexOf(lineTerminator,currentIndex);
		if(terminatePos!=-1) {
			quoteCounts+=currentLine.substring(currentIndex, terminatePos).chars().filter(num -> num == stringQuote).count();
			if(quoteCounts % 2 ==0) {
				return terminatePos;
			}else {
				if(terminatePos<currentLine.length()-1)
					return findTerminatePosition(currentLine,quoteCounts,terminatePos+1);
				else
					return -1;
			}
		}else
			return terminatePos;
	}
	private void executeAllBatch(Statement stmt, List<String> queriesList) {
		try {
			stmt.executeBatch();
			connection.commit();
		} catch (SQLException e) {
			try {
				connection.rollback();
				executeQueriesOneByOne(stmt,queriesList);
			} catch (SQLException e1) {
				e1.printStackTrace();
			}

		}

	}
	private void executeQueriesOneByOne(Statement stmt, List<String> queriesList) {
		try {
			Statement singleStmt=connection.createStatement();
			for(String query:queriesList) {
				try {
					singleStmt.execute(query);
					connection.commit();
				}catch (SQLException e) {
					System.out.println("Error in Executing Query: "+query);
					connection.rollback();
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
}
