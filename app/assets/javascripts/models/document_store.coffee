class DocumentStore
  constructor: () ->
    @documents = {}
    @_counts = {}

  add: (document) ->
    documentid = document.id
    if @_counts[documentid]?
      @_counts[documentid] += 1
    else
      @documents[document.id] = document
      @_counts[documentid] = 1

  remove: (document) ->
    documentid = document.id

    @_counts[documentid]--
    if @_counts[documentid] == 0
      delete @_counts[documentid]
      delete @documents[documentid]

  add_doclist: (doclist, documents) ->
    this.add(documents[docid]) for docid in doclist.docids
    undefined

  remove_doclist: (doclist) ->
    this.remove(@documents[docid]) for docid in doclist.docids
    undefined

exports = require.make_export_object('models/document_store')
exports.DocumentStore = DocumentStore
