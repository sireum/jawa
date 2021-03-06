/*******************************************************************************
 * Copyright (c) 2013 - 2016 Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Detailed contributors are listed in the CONTRIBUTOR.md
 ******************************************************************************/
package org.sireum.jawa.sjc.util.refactoring

import org.sireum.jawa.io.AbstractFile
import org.sireum.jawa.sjc.parser.CompilationUnit

/**
 * @author fgwei
 */
trait CompilerAccess {

  val global: org.sireum.jawa.sjc.interactive.Global

  def compilationUnitOfFile(f: AbstractFile): Option[CompilationUnit]
}
