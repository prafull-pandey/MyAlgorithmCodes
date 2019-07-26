package com.pandey.script;

public class MainClass {

	public static void main(String[] args) {
		ReadAndExecuteScript raes=new ReadAndExecuteScript("D:\\myQuery.sql");
		raes.createConnection();
		raes.readScriptFileAndExecute();

	}

}
