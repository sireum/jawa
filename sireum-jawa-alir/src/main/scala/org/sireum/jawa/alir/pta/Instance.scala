/*
Copyright (c) 2013-2014 Fengguo Wei & Sankardas Roy, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/
package org.sireum.jawa.alir.pta

import org.sireum.util._
import org.sireum.jawa.alir.Context
import org.sireum.jawa.ObjectType

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
abstract class Instance{
  def typ: ObjectType
  def defSite: Context
  def isNull: Boolean = false
  def ===(ins: Instance): Boolean = this == ins
  def clone(newDefSite: Context): Instance
}


final case class ClassInstance(classtyp: ObjectType, defSite: Context) extends Instance{
  override def clone(newDefSite: Context): Instance = ClassInstance(classtyp, newDefSite)
  def typ = ObjectType("java.lang.Class", 0)
  def getName = classtyp.jawaName
  override def ===(ins: Instance): Boolean = {
    if(ins.isInstanceOf[ClassInstance]) ins.asInstanceOf[ClassInstance].getName.equals(getName)
    else false
  }
  override def toString: String = getName + ".class@" + this.defSite.getCurrentLocUri
}

final case class UnknownInstance(baseTyp: ObjectType, defSite: Context) extends Instance{
  override def clone(newDefSite: Context): Instance = UnknownInstance(baseTyp, newDefSite)
  def typ: ObjectType = baseTyp
  override def toString: String = baseTyp + "*" + "@" + defSite.getCurrentLocUri
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class PTAInstance(typ: ObjectType, defSite: Context, isNull_ : Boolean) extends Instance{
  override def clone(newDefSite: Context): Instance = PTAInstance(typ, newDefSite, isNull_)
  override def isNull: Boolean = isNull_
  override def toString: String = {
    val sb = new StringBuilder
    sb.append(this.typ + "@")
    sb.append(this.defSite.getCurrentLocUri)
    sb.toString.intern()
  }
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class PTATupleInstance(left: Instance, right: Instance, defSite: Context) extends Instance{
  override def clone(newDefSite: Context): Instance = PTATupleInstance(left, right, newDefSite)
  def typ: ObjectType = new ObjectType("Tuple")
  override def toString: String = {
    val sb = new StringBuilder
    sb.append(this.typ + "@")
    sb.append(this.defSite.getCurrentLocUri)
    sb.toString.intern()
  }
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
abstract class PTAAbstractStringInstance(defSite: Context) extends Instance{
  def typ: ObjectType = ObjectType("java.lang.String", 0) 
  override def toString: String = this.typ + ":abstract@" + this.defSite.getCurrentLocUri
}

/**
 * PTAPointStringInstance represents a general String instance whose content can be any string i.e. reg expression "*"
 * 
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class PTAPointStringInstance(defSite: Context) extends PTAAbstractStringInstance(defSite){
  override def clone(newDefSite: Context): Instance = PTAPointStringInstance(newDefSite)
  override def ===(ins: Instance): Boolean = {
    if(ins.isInstanceOf[PTAPointStringInstance]) true
    else if(ins.isInstanceOf[PTAConcreteStringInstance]) true
    else false
  }
  override def toString: String = this.typ + ":*@" + this.defSite.getCurrentLocUri
}

/**
 * @author <a href="mailto:fgwei@k-state.edu">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
final case class PTAConcreteStringInstance(string: String, defSite: Context) extends PTAAbstractStringInstance(defSite){
  override def clone(newDefSite: Context): Instance = PTAConcreteStringInstance(string, newDefSite)
  override def ===(ins: Instance): Boolean = {
    if(ins.isInstanceOf[PTAConcreteStringInstance]) ins.asInstanceOf[PTAConcreteStringInstance].string.equals(string)
    else if(ins.isInstanceOf[PTAPointStringInstance]) true
    else false
  }
  override def toString: String = {
    val sb = new StringBuilder
    sb.append(this.typ + ":")
    sb.append("\"" + {if(this.string.length > 30)this.string.substring(0, 30) + ".." else this.string} + "\"@")
    sb.append(this.defSite.getCurrentLocUri)
    sb.toString.intern()
  }
}

