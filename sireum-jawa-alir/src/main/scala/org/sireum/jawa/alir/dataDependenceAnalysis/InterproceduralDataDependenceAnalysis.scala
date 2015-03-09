/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.alir.dataDependenceAnalysis

import org.sireum.pilar.ast._
import org.sireum.util._
import org.sireum.jawa.Center
import org.sireum.jawa.MessageCenter._
import org.sireum.jawa.alir.pta.NullInstance
import org.sireum.jawa.alir.pta.UnknownInstance
import org.sireum.jawa.JawaProcedure
import org.sireum.alir.ReachingDefinitionAnalysis
import org.sireum.alir.ControlFlowGraph
import org.sireum.alir.ParamDefDesc
import org.sireum.alir.LocDefDesc
import org.sireum.alir.InitDefDesc
import org.sireum.jawa.alir.Context
import org.sireum.alir.DefDesc
import org.sireum.jawa.alir.controlFlowGraph._
import org.sireum.jawa.PilarAstHelper
import org.sireum.alir.AlirEdge
import org.sireum.jawa.util.StringFormConverter
import org.sireum.jawa.alir.sideEffectAnalysis.InterProceduralSideEffectAnalysisResult
import org.sireum.jawa.alir.interProcedural.Callee
import org.sireum.jawa.alir.interProcedural.InstanceCallee
import org.sireum.jawa.alir.LibSideEffectProvider
import org.sireum.jawa.alir.pta.Instance
import java.io.PrintWriter
import org.sireum.jawa.alir.pta.PTAResult
import org.sireum.jawa.alir.pta.VarSlot
import org.sireum.jawa.alir.pta.FieldSlot
import org.sireum.jawa.alir.pta.ArraySlot

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
trait InterproceduralDataDependenceInfo{
  def getIddg : InterProceduralDataDependenceGraph[InterproceduralDataDependenceAnalysis.Node]
  def getDependentPath(src : InterproceduralDataDependenceAnalysis.Node, dst : InterproceduralDataDependenceAnalysis.Node) : IList[InterproceduralDataDependenceAnalysis.Edge]
  def isDependent(src : InterproceduralDataDependenceAnalysis.Node, dst : InterproceduralDataDependenceAnalysis.Node) : Boolean
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
object InterproceduralDataDependenceAnalysis {
  final val TITLE = "InterproceduralDataDependenceAnalysis"
  type Node = IDDGNode
  type Edge = AlirEdge[Node]
  
	def apply(cg : InterproceduralControlFlowGraph[CGNode], 
	    			ptaresult : PTAResult) : InterproceduralDataDependenceInfo = build(cg, ptaresult)
	
	def build(cg : InterproceduralControlFlowGraph[CGNode], 
	    			ptaresult : PTAResult) : InterproceduralDataDependenceInfo = {
    
    class Iddi(iddg : InterProceduralDataDependenceGraph[Node]) extends InterproceduralDataDependenceInfo{
      def getIddg : InterProceduralDataDependenceGraph[InterproceduralDataDependenceAnalysis.Node] = iddg
		  def getDependentPath(src : InterproceduralDataDependenceAnalysis.Node, dst : InterproceduralDataDependenceAnalysis.Node) : IList[InterproceduralDataDependenceAnalysis.Edge] = {
        iddg.findPath(src, dst)
      }
		  def isDependent(src : InterproceduralDataDependenceAnalysis.Node, dst : InterproceduralDataDependenceAnalysis.Node) : Boolean = {
		    getDependentPath(src, dst) != null
		  }
    }
    val irdaResult = InterproceduralReachingDefinitionAnalysis(cg)
	  val iddg = new InterProceduralDataDependenceGraph[Node]
	  iddg.initGraph(cg)
	  iddg.nodes.foreach{
	    node =>
	      var targetNodes : ISet[Node] = isetEmpty
	      if(node != iddg.entryNode){
	        node match{
	          case en : IDDGEntryParamNode =>
	            val cgN = cg.getCGEntryNode(en.getContext)
	            val cgTarN = cg.predecessors(cgN)
	            targetNodes ++= cgTarN.map(n => iddg.findDefSite(n.getContext, en.position))
	          case en : IDDGExitParamNode =>
	            val cgN = cg.getCGExitNode(en.getContext)
	            val proc =  Center.getProcedureWithoutFailing(cgN.getOwner)
	            val procName = en.paramName
	            val irdaFacts = irdaResult(cgN)
	            targetNodes ++= searchRda(procName, en, irdaFacts, iddg)
	          case cn : IDDGCallArgNode =>
	            val cgN = cg.getCGCallNode(cn.getContext)
				      val irdaFacts = irdaResult(cgN)
				      targetNodes ++= processCallArg(cn, ptaresult, irdaFacts, iddg)
	          case rn : IDDGReturnArgNode =>
	            val cgN = cg.getCGReturnNode(rn.getContext)
	            val cgTarN = cg.predecessors(cgN)
	            cgTarN.foreach{
	              N =>
	                N match{
	                  case cn : CGCallNode =>
	                    targetNodes += iddg.findDefSite(cn.getContext, rn.position)
	                  case en : CGExitNode =>
	                    targetNodes += iddg.findDefSite(en.getContext, rn.position)
	                  case _ =>
	                }
	            }
	          case rn : IDDGReturnVarNode =>
	          case vn : IDDGVirtualBodyNode =>
	            val cgN = vn.cgN
	            val idEntNs = iddg.getIDDGCallArgNodes(cgN)
	            targetNodes ++= idEntNs
				      val irdaFacts = irdaResult(cgN)
	            targetNodes ++= processVirtualBody(vn, ptaresult, irdaFacts, iddg)
	          case ln : IDDGNormalNode =>
	            val cgN = cg.getCGNormalNode(ln.getContext)
	            val ownerProc = Center.getProcedureWithoutFailing(ln.getOwner)
				      val loc = ownerProc.getProcedureBody.location(ln.getLocIndex)
				      val irdaFacts = irdaResult(cgN)
				      targetNodes ++= processLocation(node, loc, ptaresult, irdaFacts, iddg)
	          case a => 
	        }
	      }
	      targetNodes.foreach(tn=>iddg.addEdge(node, tn))
	  }
	  
	  msg_normal(TITLE, "[IDDG building done!]")
//	  iddg.toDot(new PrintWriter(System.out))
	  new Iddi(iddg)
	}
  
  def processCallArg(callArgNode : IDDGCallArgNode,
      							ptaresult : PTAResult, 
		    						irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
		    						iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
    val result = msetEmpty[Node]
    result ++= searchRda(callArgNode.argName, callArgNode, irdaFacts, iddg)
    val argSlot = VarSlot(callArgNode.argName)
    val inss = ptaresult.pointsToSet(argSlot, callArgNode.getContext)
    inss.foreach(ins => result += iddg.findDefSite(ins.getDefSite))
// Foeget why I do like this way???
//    val calleeSet = callArgNode.getCalleeSet
//    
//    calleeSet.foreach{
//      callee =>
//        result ++= searchRda(callArgNode.argName, callArgNode, irdaFacts, iddg)
//        val argSlot = VarSlot(callArgNode.argName)
//        val argFacts = 
//          if(callee.isInstanceOf[InstanceCallee] && callArgNode.position == 0) Set(RFAFact(argSlot, callee.asInstanceOf[InstanceCallee].ins))
//          else rfaFacts.filter(fact=> argSlot == fact.s)
//        argFacts.foreach{case RFAFact(slot, ins) => result += iddg.findDefSite(ins.getDefSite)}
//    }
    result.toSet
  }
  
  def processVirtualBody(virtualBodyNode : IDDGVirtualBodyNode,
      							ptaresult : PTAResult, 
		    						irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
		    						iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
    val result = msetEmpty[Node]
    val calleeSet = virtualBodyNode.getCalleeSet
    calleeSet.foreach{
      callee =>
        val calleep = callee.callee
        if(calleep.getDeclaringRecord.isFrameworkRecord || calleep.getDeclaringRecord.isThirdPartyLibRecord){
          val sideEffectResult = if(LibSideEffectProvider.isDefined) LibSideEffectProvider.ipsear.result(calleep.getSignature)
          											 else None
          for(i <- 0 to virtualBodyNode.argNames.size - 1){
            val argSlot = VarSlot(virtualBodyNode.argNames(i))
	          val argInss = ptaresult.pointsToSet(argSlot, virtualBodyNode.getContext)
	          argInss.foreach (ins => result += iddg.findDefSite(ins.getDefSite))
	          if(sideEffectResult.isDefined){
	            val readmap = sideEffectResult.get.readMap
//	            val writemap = sideEffectResult.get.writeMap
	            val position = i
	            val fields = readmap.getOrElse(position, Set()) 
//	            ++ writemap.getOrElse(position, Set())
	            argInss.foreach{
	              case argIns =>
	                fields.foreach{
	                  f => 
	                    val fs = FieldSlot(argIns, f)
	                    val argRelatedValue = ptaresult.getRelatedInstances(fs, virtualBodyNode.getContext)
                      argRelatedValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
		                  argIns.getFieldsUnknownDefSites.foreach{
		                  	case (defsite, udfields) =>
		                  	  if(udfields.contains("ALL")) result += iddg.findDefSite(defsite)
		                  	  else if(udfields.contains(f)) result += iddg.findDefSite(defsite)
		                	}
	                }
	            }
	          } else if(calleep.isConcrete) {
	            val argRelatedValue = ptaresult.getRelatedHeapInstances(argInss, virtualBodyNode.getContext)
	            argRelatedValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
	          }
          }
        }
    }
    result.toSet
  }
	
	def processLocation(node : Node, 
	    						loc : LocationDecl, 
	    						ptaresult : PTAResult, 
	    						irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
	    						iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
	  var result = isetEmpty[Node]
	  loc match{
		  case al : ActionLocation =>
	      al.action match {
	        case aa : AssignAction =>
	          val lhss = PilarAstHelper.getLHSs(aa)
			      val rhss = PilarAstHelper.getRHSs(aa)
			      result ++= processLHSs(node, lhss, ptaresult, irdaFacts, iddg)
			      result ++= processRHSs(node, rhss, ptaresult, irdaFacts, iddg)
	        case _ =>
	      }
	    case jl : JumpLocation =>
	      jl.jump match{
	        case t : CallJump if t.jump.isEmpty =>
			      val lhss = PilarAstHelper.getLHSs(t)
			      val rhss = PilarAstHelper.getRHSs(t)
			      result ++= processLHSs(node, lhss, ptaresult, irdaFacts, iddg)
			      result ++= processRHSs(node, rhss, ptaresult, irdaFacts, iddg)
			    case gj : GotoJump =>
			    case rj : ReturnJump =>
			      if (rj.exp.isDefined) {
		          processExp(node, rj.exp.get, ptaresult, irdaFacts, iddg)
		        }
			    case ifj : IfJump =>
			      for (ifThen <- ifj.ifThens) {
              processCondition(node, ifThen.cond, ptaresult, irdaFacts, iddg)
            }
            if (ifj.ifElse.isEmpty) {
            } else {
            }
			    case sj : SwitchJump =>
			      for (switchCase <- sj.cases) {
              processCondition(node, switchCase.cond, ptaresult, irdaFacts, iddg)
            }
            if (sj.defaultCase.isEmpty) {
            } else {
            }
	      }
	    case _ =>
	  }
	  result
	}
	
	def processLHSs(node : Node, lhss : Seq[Exp], ptaresult : PTAResult, irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
    var result = isetEmpty[Node]
	  lhss.foreach{
	    lhs =>
	      lhs match{
	        case ne : NameExp =>
          case ae : AccessExp =>
            val baseSlot = ae.exp match {
              case ne : NameExp => 
                result ++= searchRda(ne.name.name, node, irdaFacts, iddg)
//                VarSlot(ne.name.name)
              case _ => throw new RuntimeException("Wrong exp: " + ae.exp)
            }
//            val baseValue = rfaFacts.filter(f => f.s == baseSlot).map(f => f.v)
//            baseValue.foreach{
//              ins =>
//                val defSite = ins.getDefSite
//                result += iddg.findDefSite(defSite)
//            }
          case ie : IndexingExp =>
            val baseSlot = ie.exp match {
              case ine : NameExp =>
                result ++= searchRda(ine.name.name, node, irdaFacts, iddg)
//                VarSlot(ine.name.name)
              case _ => throw new RuntimeException("Wrong exp: " + ie.exp)
            }
//            val baseValue = rfaFacts.filter(f => f.s == baseSlot).map(f => f.v)
//            baseValue.foreach{
//              ins =>
//                val defSite = ins.getDefSite
//                result += iddg.findDefSite(defSite)
//            }
          case _=>
	      }
	  }
    result
	}
	
	def processRHSs(node : Node, 
	    			rhss : Seq[Exp], 
	    			ptaresult : PTAResult, 
	    			irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
	    			iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
    var result = isetEmpty[Node]
    if(!rhss.isEmpty)
    	result ++= rhss.map(processExp(node, _, ptaresult, irdaFacts, iddg)).reduce(iunion[Node])
    result
	}
	
	def processExp(node : Node, 
	    		exp : Exp, 
	    		ptaresult : PTAResult, 
	    		irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
	    		iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
	  var result = isetEmpty[Node]
	  exp match{
      case ne : NameExp =>
        result ++= searchRda(ne.name.name, node, irdaFacts, iddg)
        val slot = VarSlot(ne.name.name)
        val value = ptaresult.pointsToSet(slot, node.getContext)
        value.foreach{
          ins =>
            val defSite = ins.getDefSite
            result += iddg.findDefSite(defSite)
        }
      case ae : AccessExp =>
        val fieldSig = ae.attributeName.name
        val baseSlot = ae.exp match {
          case ne : NameExp => 
            result ++= searchRda(ne.name.name, node, irdaFacts, iddg)
            VarSlot(ne.name.name)
          case _ => throw new RuntimeException("Wrong exp: " + ae.exp)
        }
        val baseValue = ptaresult.pointsToSet(baseSlot, node.getContext)
        baseValue.foreach{
          ins =>
            result += iddg.findDefSite(ins.getDefSite)
            if(!ins.isInstanceOf[NullInstance]){ // if(!ins.isInstanceOf[NullInstance] && !ins.isInstanceOf[UnknownInstance]){
              val recName = StringFormConverter.getRecordNameFromFieldSignature(fieldSig)
              val rec = Center.resolveRecord(recName, Center.ResolveLevel.HIERARCHY)
              if(!rec.isArray){ // if(!rec.isUnknown && !rec.isArray){
	              val fName = rec.getField(fieldSig).getName
		            val fieldSlot = FieldSlot(ins, fName)
	              val fieldValue = ptaresult.pointsToSet(fieldSlot, node.getContext)
                fieldValue.foreach(fIns => result += iddg.findDefSite(fIns.getDefSite))
                ins.getFieldsUnknownDefSites.foreach{
                	case (defsite, fields) =>
                	  if(fields.contains("ALL")) result += iddg.findDefSite(defsite)
                	  else if(fields.contains(fName)) result += iddg.findDefSite(defsite)
              	}
              }
            }
        }
      case ie : IndexingExp =>
        val baseSlot = ie.exp match {
          case ine : NameExp =>
            result ++= searchRda(ine.name.name, node, irdaFacts, iddg)
            VarSlot(ine.name.name)
          case _ => throw new RuntimeException("Wrong exp: " + ie.exp)
        }
        val baseValue = ptaresult.pointsToSet(baseSlot, node.getContext)
        baseValue.foreach{
          ins =>
            result += iddg.findDefSite(ins.getDefSite)
            val arraySlot = ArraySlot(ins)
            val arrayValue = ptaresult.getRelatedInstances(arraySlot, node.getContext)
            arrayValue.foreach(aIns => result += iddg.findDefSite(aIns.getDefSite))
        }
      case ce : CastExp =>
        ce.exp match{
          case ice : NameExp =>
            result ++= searchRda(ice.name.name, node, irdaFacts, iddg)
            val slot = VarSlot(ice.name.name)
            val value = ptaresult.pointsToSet(slot, node.getContext)
            value.foreach{
              ins =>
                val defSite = ins.getDefSite
                result += iddg.findDefSite(defSite)
            }
          case nle : NewListExp => 
          case _ => throw new RuntimeException("Wrong exp: " + ce.exp)
        }
      case ce : CallExp =>
        val calleeSet = if(node.isInstanceOf[IDDGInvokeNode]) node.asInstanceOf[IDDGInvokeNode].getCalleeSet else Set[Callee]()
        ce.arg match{
	        case te : TupleExp => 
	          val argSlots = te.exps.map{
	            exp =>
	              exp match{
			            case ne : NameExp => 
			              result ++= searchRda(ne.name.name, node, irdaFacts, iddg)
			              VarSlot(ne.name.name)
			            case _ => VarSlot(exp.toString)
			          }
	          }
	          calleeSet.foreach{
	            callee =>
	              val calleep = callee.callee
	              if(calleep.getDeclaringRecord.isFrameworkRecord || calleep.getDeclaringRecord.isThirdPartyLibRecord){
	                val sideEffectResult = if(LibSideEffectProvider.isDefined) LibSideEffectProvider.ipsear.result(calleep.getSignature)
	                											 else None
		              for(i <- 0 to argSlots.size - 1){
				            val argSlot = argSlots(i)
			              val argValue = ptaresult.pointsToSet(argSlot, node.getContext)
			              argValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
			              if(sideEffectResult.isDefined){
			                val readmap = sideEffectResult.get.readMap
			                val writemap = sideEffectResult.get.writeMap
			                val position = i
			                val fields = readmap.getOrElse(position, Set()) ++ writemap.getOrElse(position, Set())
			                argValue.foreach{
			                  argIns =>
			                    fields.foreach{
			                      f => 
			                        val fs = FieldSlot(argIns, f)
			                        val argRelatedValue = ptaresult.getRelatedInstances(fs, node.getContext)
                              argRelatedValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
						                  argIns.getFieldsUnknownDefSites.foreach{
						                  	case (defsite, udfields) =>
						                  	  if(udfields.contains("ALL")) result += iddg.findDefSite(defsite)
						                  	  else if(udfields.contains(f)) result += iddg.findDefSite(defsite)
						                	}
			                    }
			                }
			              } else if(calleep.isConcrete) {
				              val argRelatedValue = ptaresult.getRelatedHeapInstances(argValue, node.getContext)
                      argRelatedValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
			              }
				          }
	              } else {
	                for(i <- 0 to argSlots.size - 1){
				            val argSlot = argSlots(i)
			              val argValue = ptaresult.getRelatedInstances(argSlot, node.getContext)
			              argValue.foreach{ins => result += iddg.findDefSite(ins.getDefSite)}
	                }
	              }
	          }
	          result
	        case _ => throw new RuntimeException("wrong exp type: " + ce + "  " + ce.arg)
	      }
      case _=>
    }
	  result
	}
	
	def processCondition(node : Node, 
	    			cond : Exp, 
	    			ptaresult : PTAResult, 
	    			irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], 
	    			iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
	  var result = isetEmpty[Node]
	  cond match{
	    case be : BinaryExp =>
	      result ++= processExp(node, be.left, ptaresult, irdaFacts, iddg)
	      result ++= processExp(node, be.right, ptaresult, irdaFacts, iddg)
	    case _ =>
	  }
	  result
	}
	
	def searchRda(varName : String, node : Node, irdaFacts : ISet[InterproceduralReachingDefinitionAnalysis.IRDFact], iddg : InterProceduralDataDependenceGraph[Node]) : ISet[Node] = {
    var result : ISet[Node] = isetEmpty
    val varN = varName
    irdaFacts.foreach{
      case ((slot, defDesc), tarContext)=> 
        if(varN == slot.toString()){
          defDesc match {
            case pdd : ParamDefDesc =>
              pdd.locUri match{
                case Some(locU) =>
                  result += iddg.findDefSite(tarContext, pdd.paramIndex)
                case None =>
                  throw new RuntimeException("Unexpected ParamDefDesc: " + pdd)
              }
            case ldd : LocDefDesc => 
              ldd.locUri match {
                case Some(locU) =>
                  result += iddg.findDefSite(tarContext)
                case None =>
                  throw new RuntimeException("Unexpected LocDefDesc: " + ldd)
              }
            case dd : DefDesc =>
              if(dd.isDefinedInitially && !varName.startsWith("@@")){
                val indexs : MSet[Int] = msetEmpty
                val owner = Center.getProcedureWithoutFailing(node.getOwner)
                val paramNames = owner.getParamNames
                val paramTyps = owner.getParamTypes
                var index = 0
                for(i <- 0 to paramNames.size - 1){
                  val paramName = paramNames(i)
                  val ptypName = paramTyps(i).name
                  if(paramName == varName) indexs += index
                  if(ptypName == "double" || ptypName == "long"){
                  	index += 1
                  	if(paramName == varName) indexs += index
                  }
                  index += 1
                }
                
	              result ++= indexs.map(i => iddg.findDefSite(tarContext, i))
              }
          }
        }
  	}
    result
  }

}