/*******************************************************************************
 * Copyright (c) 2013 - 2016 Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Main Contributors:
 *    Fengguo Wei - Argus Lab @ University of South Florida
 *    Sankardas Roy - Bowling Green State University
 *    
 * Contributors:
 *    Robby - Santos Lab @ Kansas State University
 *    Wu Zhou - Fireeye
 *    Fengchi Lin - Chinese People's Public Security University
 ******************************************************************************/
package org.sireum.jawa.alir.dataFlowAnalysis

import org.sireum.util.ISet
import org.sireum.jawa.alir.controlFlowGraph.ICFGNode

/**
 * Provide an Interface to let the developer get data facts corresponding
 * to each statement.
 * 
 * @author Fengguo Wei
 */
trait InterProceduralDataFlowAnalysisResult[LatticeElement] {
  def entrySet : ICFGNode => ISet[LatticeElement]
}
