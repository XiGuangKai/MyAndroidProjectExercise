/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense.lua;

import java.io.IOException;

import org.luaj.vm2.lib.jse.JseIoLib;

import com.motorola.desense.ScriptExecutor;

public class DesenseIoLib extends JseIoLib {

	final String directory;
	final String script_name;

	public DesenseIoLib(String d, String s) { directory = d; script_name = s; }

	@Override
	protected File openFile(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException {
		return super.openFile(directory + ". " + filename, readMode, appendMode, updateMode, binaryMode);
	}

	public File openFileAbsolute(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException {
		return super.openFile(filename, readMode, appendMode, updateMode, binaryMode);
	}

}
