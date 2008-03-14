package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.12.2003
 * Time: 16:49:25
 * To change this template use Options | File Templates.
 */
public class AllClassesGetter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.filters.getters.AllClassesGetter");

  private final ElementFilter myFilter;
  private static final SimpleInsertHandler INSERT_HANDLER = new SimpleInsertHandler() {
    public int handleInsert(final Editor editor, final int startOffset, final LookupElement item, final LookupElement[] allItems, final TailType tailType) {
      final PsiClass psiClass = (PsiClass)item.getObject();
      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return endOffset;

      if (endOffset == 0) return endOffset;

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
      final PsiFile file = psiDocumentManager.getPsiFile(document);
      final PsiElement element = file.findElementAt(endOffset - 1);
      if (element == null) return endOffset;


      final RangeMarker toDelete = DefaultInsertHandler.insertSpace(endOffset, document);
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);
      boolean insertFqn = true;
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, resolveReference(psiReference))) {
          insertFqn = false;
        }
        else {
          try {
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                endOffset = psiElement.getTextRange().getEndOffset();
              }
              insertFqn = false;
            }
          }
          catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (toDelete.isValid()) {
        document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
      }

      if (insertFqn) {
        int i = endOffset - 1;
        while (i >= 0) {
          final char ch = document.getCharsSequence().charAt(i);
          if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
          i--;
        }
        document.replaceString(i + 1, endOffset, qname);
        endOffset = i + 1 + qname.length();
      }

      //todo[peter] hack, to deal with later
      if (psiClass.isAnnotationType()) {
        // Check if someone inserts annotation class that require @
        psiDocumentManager.commitAllDocuments();
        PsiElement elementAt = file.findElementAt(startOffset);
        final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

        if (elementAt instanceof PsiIdentifier &&
            ( PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null || //we are inserting '@' only in annotation parameters
              (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile) // top level annotation without @
            )
            && isAtTokenNeeded(editor, startOffset)) {
          PsiElement parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, PsiCodeBlock.class);
          if (parent == null && parentElement instanceof PsiErrorElement) {
            PsiElement nextElement = parentElement.getNextSibling();
            if (nextElement instanceof PsiWhiteSpace) nextElement = nextElement.getNextSibling();
            if (nextElement instanceof PsiClass) parent = nextElement;
          }

          if (parent instanceof PsiModifierListOwner) {
            document.insertString(elementAt.getTextRange().getStartOffset(), "@");
            endOffset++;
          }
        }
      }

      if (tailType == TailTypes.SMART_COMPLETION) {
        document.insertString(endOffset, "(");
        endOffset++;
      }
      editor.getCaretModel().moveToOffset(endOffset);

      return endOffset;
    }

    private boolean isAtTokenNeeded(Editor editor, int startOffset) {
      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(startOffset);
      LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
      iterator.retreat();
      if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
      return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
    }

  };

  private static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public void getClasses(final PsiElement context, CompletionContext completionContext, CompletionResultSet<LookupElement> set) {
    if(context == null || !context.isValid()) return;

    String prefix = context.getText().substring(0, completionContext.getStartOffset() - context.getTextRange().getStartOffset());
    final int i = prefix.lastIndexOf('.');
    String packagePrefix = "";
    if (i > 0) {
      packagePrefix = prefix.substring(0, i);
    }

    final PsiManager manager = context.getManager();
    final Set<String> qnames = new THashSet<String>();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiShortNamesCache cache = facade.getShortNamesCache();

    final GlobalSearchScope scope = context.getContainingFile().getResolveScope();
    final String[] names = cache.getAllClassNames(true);

    Arrays.sort(names, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareToIgnoreCase(o2);
      }
    });

    boolean lookingForAnnotations = false;
    final PsiElement prevSibling = context.getParent().getPrevSibling();
    if (prevSibling instanceof PsiJavaToken &&
        ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.AT) {
      lookingForAnnotations = true;
    }

    final CamelHumpMatcher matcher = new CamelHumpMatcher(completionContext.getPrefix());
    for (final String name : names) {
      if (!matcher.prefixMatches(name)) continue;

      for (PsiClass psiClass : cache.getClassesByName(name, scope)) {
        if (lookingForAnnotations && !psiClass.isAnnotationType()) continue;

        if (JavaCompletionUtil.isInExcludedPackage(psiClass)) continue;

        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(packagePrefix)) continue;

        if (!myFilter.isAcceptable(psiClass, context)) continue;

        if (qnames.add(qualifiedName)) {
          set.addElement(createLookupItem(psiClass));
        }
      }
    }
  }

  protected LookupItem<PsiClass> createLookupItem(final PsiClass psiClass) {
    final LookupItem<PsiClass> item = LookupElementFactoryImpl.getInstance().createLookupElement(psiClass).setInsertHandler(INSERT_HANDLER);
    item.setAttribute(LookupItem.FORCE_SHOW_FQN_ATTR, "");
    return item;
  }

}
