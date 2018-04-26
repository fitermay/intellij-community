// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve

import com.google.common.collect.Lists
import com.intellij.facet.FacetManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.isNullOrEmpty
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil
import com.jetbrains.python.facet.PythonPathContributingFacet
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyImportResolver
import com.jetbrains.python.psi.resolve.ImportResultKind.PYTHON
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.sdk.PySdkUtil
import java.util.*


private typealias ImportResults = EnumMap<ImportResultKind, List<PsiElement>>

private enum class ImportResultKind {
  PYTHON,
  FOREIGN,
  SKELETONS,
  STUBS
}

private fun ImportResults.addResults(t: ImportResultKind, l: List<PsiElement>) =
  EnumMap(this).let {
    it[t] = (it[t] ?: emptyList()) + l
  }

private sealed class FileSystemImportResult

private data class NamespacePackage(val dunderPath: List<PsiDirectory>) : FileSystemImportResult()
private sealed class SingleItemImportResult : FileSystemImportResult()
private data class PackageImport(val mod: PsiDirectory) : SingleItemImportResult()
private data class ModuleImport(val mod: PyFile) : SingleItemImportResult()
private class PEP420Resolver(private val name: String,
                             private val footHold: PyQualifiedNameResolveFootHold,
                             private val isTopLevel: Boolean, private val withoutStubs: Boolean = false) {
  private val dunderPath: MutableList<PsiDirectory> = mutableListOf()
  private var nonNSResult: SingleItemImportResult? = null
  fun visitRoot(root: VirtualFile): Boolean {
    if (!root.isValid || !root.isDirectory) {
      return true
    }

    val rootDir = footHold.psiManager.findDirectory(root) ?: return true

    val foundPackage = rootDir.findSubdirectory(name)
    if (foundPackage != null) {

      val hasDunderInit = PyUtil.turnDirIntoInit(foundPackage) != null
      if (hasDunderInit) {
        nonNSResult = PackageImport(foundPackage)
        return false
      }
    }

    val foundModule = findPyFileInDir(rootDir, name, withoutStubs)
    if (foundModule != null) {
      nonNSResult = ModuleImport(foundModule)
      return false
    }
    if (foundPackage != null) {
      if (isNamespacePackage(foundPackage)) {
        dunderPath.add(foundPackage)
        return true
      }
    }

    if (isTopLevel && rootDir.name == name && isAcceptRootAsTopLevelPackage(footHold)) {
      if (isNamespacePackage(rootDir)) {
        dunderPath.add(rootDir)
        return true
      }
      val hasDunderInit = PyUtil.turnDirIntoInit(rootDir) != null
      if (hasDunderInit) {
        nonNSResult = PackageImport(rootDir)
        return false
      }
    }

    return true

  }

  val hasDefiniteResult = nonNSResult != null
  val result: FileSystemImportResult?
    get() {
      nonNSResult?.let { return it }
      dunderPath.takeIf { it.isNotEmpty() }?.let { return NamespacePackage(it) }
      return null
    }

}

private class TopLevelRootVisitor(  name: String,
                                    footHold: PyQualifiedNameResolveFootHold) : RootVisitor {
  private val resolver = PEP420Resolver(name, footHold, true)
  override fun visitRoot(root: VirtualFile, module: Module?, sdk: Sdk?, isModuleSource: Boolean): Boolean {
    if (!root.isValid ||
        root == PyUserSkeletonsUtil.getUserSkeletonsDirectory() || PyTypeShed.isInside(root)) {
      return true
    }

    if (!isModuleSource && sdk != null){
      val skeletonsDir = PySdkUtil.findSkeletonsDir(sdk)
      if (skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, root, false)){
        return true
      }
    }
    return resolver.visitRoot(root)
  }

  val hasDefiniteResult = resolver.hasDefiniteResult
  val result: FileSystemImportResult? = resolver.result


}

private fun resolveAbsoluteImport(qname: QualifiedName, context: PyQualifiedNameResolveContext): List<PsiElement> {


  val skeletonResults = absoluteSkeletonResults(context, qname)
  val typeShedResults = getTypeShedResults(context, qname)
  val foreignResults = foreignResults(qname, context)

  val nonPyResults = skeletonResults + typeShedResults + foreignResults


  val topLevelPackage = resolveTopLevelPackage(qname, context) ?: return nonPyResults


  resolveAt(qname, topLevelPackage, context, true)


}

private fun getTypeShedResults(context: PyQualifiedNameResolveContext,
                               qname: QualifiedName): List<PsiElement> {
  if (!context.withoutStubs) {
    val typeShedRoots = context.effectiveSdk?.let { PyTypeShed.findRootsForSdk(it) }?.mapNotNull { context.psiManager.findDirectory(it) }
    if (typeShedRoots != null) {
      return resolveInRoots(qname, typeShedRoots, context, false)
    }
  }
  return emptyList()
}

private fun absoluteSkeletonResults(context: PyQualifiedNameResolveContext,
                                    qname: QualifiedName) : List<PsiElement> {
  val skeletonsRoot = context.effectiveSdk?.let { PySdkUtil.findSkeletonsDir(it) }?.let { context.psiManager.findDirectory(it) }
  return skeletonsRoot?.let { resolveInRoots(qname, listOf(it), context, true) } ?: emptyList()
}

private fun resolveInRoots(qname: QualifiedName , roots: List<PsiDirectory>,footHold: PyQualifiedNameResolveFootHold, withoutStubs: Boolean ): List<PsiElement>{
  val resolved = resolveAt(qname, NamespacePackage(roots), footHold, withoutStubs) ?: return emptyList()
  return when (resolved) {
    is ModuleImport -> listOf(resolved.mod)
    is PackageImport -> listOf(resolved.mod)
    is NamespacePackage -> resolved.dunderPath
  }
}
private fun resolveAt(qname: QualifiedName,
                      anchor: FileSystemImportResult,
                      footHold: PyQualifiedNameResolveFootHold, withoutStubs: Boolean): FileSystemImportResult? {
  var rest = qname
  var currentResult = anchor
  loop@ while (rest.componentCount > 0) {
    val next = rest.firstComponent!!
    val resolver = PEP420Resolver(next, footHold, false, withoutStubs)
    when (currentResult) {
      is ModuleImport -> break@loop
      is PackageImport -> resolver.visitRoot(currentResult.mod.virtualFile)
      is NamespacePackage -> for (pkgDir in currentResult.dunderPath) {
        if (!resolver.visitRoot(pkgDir.virtualFile)) {
          break
        }
      }
    }
    currentResult = resolver.result ?: break@loop
    rest = rest.removeHead(1)
  }

   return currentResult.takeIf { rest.componentCount == 0 }
/*  return if (rest.componentCount == 0) {
    when (currentResult) {
      is ModuleImport -> listOf(RatedResolveResult(RatedResolveResult.RATE_HIGH, currentResult.mod))
      is PackageImport -> listOf(RatedResolveResult(RatedResolveResult.RATE_HIGH, currentResult.mod))
      is NamespacePackage -> currentResult.dunderPath.map { RatedResolveResult(RatedResolveResult.RATE_HIGH, it) }
    }
  }
  else if (rest.componentCount == 1 && currentResult is ModuleImport) {
    //Fallback for cases such as os.path''
    resolveModuleMember(currentResult.mod, rest.lastComponent!!)
  }
  else {
    emptyList()
  }*/
}

private fun resolveTopLevelPackage(qname: QualifiedName,
                                   context: PyQualifiedNameResolveContext): FileSystemImportResult? {


  val topLevelModOrPkg = qname.firstComponent ?: return null
  val visitor = TopLevelRootVisitor(topLevelModOrPkg, context)
  val sdk = context.effectiveSdk
  val module = context.module
  val footholdFile = context.footholdFile
  when {
    context.visitAllModules -> {
      for (it in ModuleManager.getInstance(context.project).modules) {
        RootVisitorHost.visitRoots(it, true, visitor)
        if (visitor.hasDefiniteResult) {
          break
        }
      }
      if (sdk != null && !visitor.hasDefiniteResult) RootVisitorHost.visitSdkRoots(sdk, visitor)
      else if (footholdFile != null && !visitor.hasDefiniteResult) RootVisitorHost.visitSdkRoots(footholdFile, visitor)
    }
    module != null -> {
      val otherSdk = sdk != context.sdk
      RootVisitorHost.visitRoots(module, otherSdk, visitor)
      if (otherSdk && sdk != null && !visitor.hasDefiniteResult) {
        RootVisitorHost.visitSdkRoots(sdk, visitor)
      }
    }
    footholdFile != null -> {
      RootVisitorHost.visitRoots(footholdFile, visitor)
    }
    sdk != null -> {
      RootVisitorHost.visitSdkRoots(sdk, visitor)
    }
    else -> throw IllegalStateException()
  }
  return visitor.result
}

private fun findPyFileInDir(dir: PsiDirectory, referencedName: String, withoutStubs: Boolean): PyFile? {
  dir.findFile("$referencedName${PyNames.DOT_PY}")?.let { return it as? PyFile }
  dir.findFile("$referencedName${PyNames.DOT_PYI}")?.takeIf { !withoutStubs }?.let { return it as PyFile? }

  val associations = FileTypeManager.getInstance().getAssociations(PythonFileType.INSTANCE)
  for (association in associations) {
    if (association is ExtensionFileNameMatcher) {
      dir.findFile("$referencedName.${association.extension}")?.let { return it as? PyFile }
    }
  }
  return null
}

private fun isAcceptRootAsTopLevelPackage(context: PyQualifiedNameResolveFootHold): Boolean {
  context.module?.let {
    FacetManager.getInstance(it).allFacets.forEach {
      if (it is PythonPathContributingFacet && it.acceptRootAsTopLevelPackage()) {
        return true
      }
    }
  }
  return false
}

private fun isNamespacePackage(element: PsiElement): Boolean {
  if (element is PsiDirectory) {
    val level = PyUtil.getLanguageLevelForVirtualFile(element.project, element.virtualFile)
    if (!level.isPython2) {
      return PyUtil.turnDirIntoInit(element) == null
    }
  }
  return false
}

private fun resolveModuleMember(file: PyFile, referencedName: String): List<RatedResolveResult> {
  val moduleType = PyModuleType(file)
  val resolveContext = PyResolveContext.defaultContext()
  val results = moduleType.resolveMember(referencedName, null, AccessDirection.READ,
                                         resolveContext) ?: return emptyList()
  return Lists.newArrayList(results)
}

private fun foreignResults(name: QualifiedName, context: PyQualifiedNameResolveContext) =
  Extensions.getExtensions(PyImportResolver.EP_NAME)
    .asSequence()
    .map { it.resolveImportReference(name, context, !context.withoutRoots) }
    .filterNotNull()
    .toList()

private fun resolveQualifiedNameNew(name: QualifiedName,
                                    context: PyQualifiedNameResolveContext): ImportResults {

  if (context.relativeLevel != -1 && !context.withoutRoots) {
    val relativeResults = resolveQualifiedNameNew(name, context.copyWithoutRoots())
    if (!relativeResults[PYTHON].isNullOrEmpty()) {
      return relativeResults
    }
    val absoluteResults = resolveQualifiedNameNew(name, context.copyWithRelative(-1))
  }

}