asUserWithDocumentSet = require('../support/asUserWithDocumentSet')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')

Url =
  index: '/documentsets'

describe 'Recluster', ->
  asUserWithDocumentSet('Recluster', 'Recluster/documents.csv')

  doDelete = (browser, title) ->
    browser
      .goToFirstDocumentSet()
      .waitForElementBy(tag: 'a', contains: title, visible: true)
      .elementByCss('>', '.toggle-popover').click()
      .listenForJqueryAjaxComplete()
      .acceptingNextAlert()
      .waitForElementByCss('.popover.in button.delete').click()
      .waitForJqueryAjaxComplete()

  describe 'after a recluster', ->
    before ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy(tag: 'a', contains: 'Add view').click()
        .waitForElementByCss('a[data-plugin-url="about:tree"]').click()
        .waitForElementBy(tag: 'input', name: 'tree_title', visible: true).type('view1')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForElementBy(tag: 'a', contains: 'view1').click() # Ensure the current tree is deselected...
        .waitForElementByCss('#tree-app-tree canvas', 5000)    # ... so that we can wait for the next one

    shouldBehaveLikeATree
      documents: [
        { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
      ]
      searches: [
        { query: 'document', nResults: 4 }
      ]

    it 'should rename properly', ->
      doRename = (browser, oldTitle, newTitle) ->
        browser
          .goToFirstDocumentSet()
          .waitForElementBy(tag: 'a', contains: oldTitle, visible: true)
          .elementByCss('>', '.toggle-popover').click()
          .elementByCss('.popover.in dd.title a.rename').click()
          .elementByCss('.popover.in dd.title input[name=title]').type(newTitle)
          .listenForJqueryAjaxComplete()
          .elementByCss('.popover.in dd.title button[type=submit]').click()
          .waitForJqueryAjaxComplete()
          .elementByCss('.popover.in a.close').click()

      verify = (browser, title) ->
        browser
          .waitForElementBy(tag: 'a', contains: 'view-renamed').should.eventually.exist
          .elementByCss('>', '.toggle-popover').click()
          .elementByCss('.popover.in dd.title').text().should.eventually.contain('view-renamed')
          .elementByCss('.popover.in a.close').click()

      doRename(@userBrowser, 'view1', 'view-renamed')
        .then(=> verify(@userBrowser, 'view-renamed'))
        .then(=> @userBrowser.goToFirstDocumentSet())
        .then(=> verify(@userBrowser, 'view-renamed'))
        .then(=> doRename(@userBrowser, 'view-renamed', 'view1'))

    it 'should delete properly', ->
      doDelete(@userBrowser, 'view1')
        .waitForElementByCss('#tree-app-tree canvas').should.eventually.exist # it selects the next tree
        .elementByCss('.view-tabs li.tree').text().should.not.eventually.contain('view1') # it deletes the tab
        .goToFirstDocumentSet()
        .waitForElementByCss('.view-tabs li.tree').text().should.not.eventually.contain('view1') # it stays deleted

  describe 'when reclustering just a tag', ->
    before ->
      @userBrowser
        .goToFirstDocumentSet()
        .waitForElementBy(tag: 'a', contains: 'Add view').click()
        .waitForElementByCss('a[data-plugin-url="about:tree"]').click()
        .waitForElementBy(tag: 'option', contains: 'foo').click()
        .elementBy(tag: 'input', name: 'tree_title', visible: true).type('view2')
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForElementBy(tag: 'a', contains: 'view2').click() # Ensure the current tree is deselected...
        .waitForElementByCss('#tree-app-tree canvas', 5000)    # ... so that we can wait for the next one

    after -> doDelete(@userBrowser, 'view2')

    shouldBehaveLikeATree
      documents: [
        { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
      ]
      searches: [
        { query: 'document', nResults: 3 }
      ]
