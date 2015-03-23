asUserUploadingFiles = require('../support/asUserUploadingFiles')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  fileUpload: '/imports/file'

describe 'FileUpload', ->

  asUserUploadingFiles('FileUpload')

  testMethods.usingPromiseChainMethods
    waitForJobsToComplete: (sleepTime) ->
      @
        .sleep(sleepTime) # async requests can time out; this won't
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 19000)

    deleteTopUpload: ->
      @
        .get(Url.index)
        .elementByCss('.actions .dropdown-toggle').click()
        .acceptingNextAlert()
        .elementByCss('.delete-document-set').click()

    loadImportedTree: (name) ->
      @
        .waitForJobsToComplete(5000)
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: name, visible: true).click()

    chooseManyFiles: ->
      [1..60].reduce(
        ((browserPromise, file) -> browserPromise.chooseFile("ManyFiles/file-#{file}.pdf")),
        @
      )


      

  describe 'after uploading files', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('FileUpload/Cat1.docx')
        .chooseFile('FileUpload/Cat2.txt')
        .chooseFile('FileUpload/Cat3.rtf')
        .chooseFile('FileUpload/Cat4.html')
        .chooseFile('FileUpload/Jules1.doc')
        .chooseFile('FileUpload/Jules2.pptx')
        .chooseFile('FileUpload/Jules3.xlsx')
        .elementBy(tag: 'button', contains: 'Done adding files', visible: true).click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true)
        .elementBy(tag: 'input', name: 'name').type('File Upload')
        .elementBy(tag: 'textarea', name: 'supplied_stop_words').type('moose frog')
        .elementBy(tag: 'textarea', name: 'important_words').type('couch face')
        .doImport()
        .waitForJobsToComplete(20000)

    after ->
      @userBrowser
        .deleteTopUpload()
    
    it 'should show document set', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy({tag: 'h3', contains: 'File Upload'}, 10000).should.eventually.exist
        
    describe 'in the default tree', ->
      before ->
        @userBrowser
          .openFileUploadPage()
          .get(Url.index)
          .waitForElementBy(tag: 'a', contains: 'File Upload', visible: true).click()

          
      shouldBehaveLikeATree
        documents: [
          { type: 'pdf', title: 'Cat1.docx' },
          { type: 'pdf', title: 'Cat2.txt' },
          { type: 'pdf', title: 'Cat3.rtf' },
          { type: 'pdf', title: 'Cat4.html' },
          { type: 'pdf', title: 'Jules1.doc' },
          { type: 'pdf', title: 'Jules2.pptx' },
          { type: 'pdf', title: 'Jules3.xlsx' }
        ]
        searches: [
          { query: 'chase', nResults: 4 }
        ]
        ignoredWords: [ 'moose', 'frog' ]
        importantWords: [ 'couch', 'face' ]

  describe 'after splitting a file into pages', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('FileUpload/Cat1.docx')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('File Upload')
        .doImport()
        .loadImportedTree('File Upload')
        
    shouldBehaveLikeATree
      documents: [
          { type: 'pdf', title: 'Cat1.docx – page 1' },
          { type: 'pdf', title: 'Cat1.docx – page 2' },
          { type: 'pdf', title: 'Cat1.docx – page 3' }
      ]
      searches: [
        { query: 'face', nResults: 3 }
      ]

    after ->
      @userBrowser
        .deleteTopUpload()


  
  describe 'after splitting many files into pages @SauceLabsKiller', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseManyFiles()
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'label', contains: 'Each page is one document', visible: 'true').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('File Upload')
        .doImport()

    it 'should create one document per page', ->
      @userBrowser
        .waitForJobsToComplete(5000)      
        .waitForElementBy(class: 'document-count' , contains: '120 documents').should.eventually.exist

    after ->
      @userBrowser
        .deleteTopUpload()
