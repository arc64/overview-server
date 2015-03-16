define [
  'backbone'
  '../helpers/DocumentHelper'
  'i18n'
], (Backbone, DocumentHelper, i18n) ->
  t = i18n.namespaced('views.Tree.show.DocumentListCursor')

  # Shows the Document corresponding to the user's cursor.
  #
  # There are three states (HTML class names) this view can be in:
  #
  # * showing-document: the cursor points at a document
  # * showing-unloaded-document: the cursor points at a document that is in the
  #   DocumentList's range but is not in the DocumentList's collection -- that
  #   is, a document that presumably we're fetching from the server.
  # * not-showing-document: the cursor does not point at a document
  #
  # The following options must be passed:
  #
  # * documentList: a DocumentList. It has a `documents` Backbone.Collection
  #   property, a `param` `DocumentListParams` property and a `length`
  #   attribute which may begin `null`.
  # * selection: A Backbone.Model with `cursorIndex` (maybe undefined integer)
  #   and `selectedIndices` (maybe-empty Array of integers). 0 is the first
  #   index.
  # * tags: a Backbone.Collection of Tag objects.
  # * documentDisplayApp: a DocumentDisplay/App constructor, such that
  #   `new options.documentDisplayApp({ el: HTMLElement })` will create an
  #   object with a `setDocument()` method.
  class DocumentListCursor extends Backbone.View
    events:
      'click a.next': '_onClickNext'
      'click a.previous': '_onClickPrevious'
      'click a.back-to-list': '_onClickBackToList'

    templates:
      root: _.template("""
        <header></header>
        <article></article>
      """)

      header: _.template("""
        <div class="document-nav">
          <div class="pager">
            <a href="#" class="previous <%= cursorIndex ? '' : 'disabled' %>"><i class="icon-chevron-left"></i> <span><%- t('previous') %></span></a>
            <h4><%= t('position_html', cursorIndex + 1, nDocuments) %></h4>
            <a href="#" class="next <%= cursorIndex + 1 < nDocuments ? '' : 'disabled' %>"><span><%- t('next') %></span> <i class="icon-chevron-right"></i></a>
          </div>
          <a href="#" class="back-to-list"><span><%- t('close') %></span> <b>×</b></a>
        </div>
        <h2><%- title %></h2>
        <ul class="tags">
          <% _.each(tags, function(tag) { %>
            <li class="tag" data-cid="<%- tag.cid %>">
              <div class="tag" style="background-color: <%= tag.get('color') %>;">
                <span class="name"><%- tag.get('name') %></span>
              </div>
            </li>
          <% }); %>
        </ul>
        <h3><%- (document && document.get('description')) ? t('description', document.get('description')) : t('description.empty') %></h3>
      """)

    initialize: ->
      throw 'Must pass options.selection, a Backbone.Model with a "cursorIndex" property' if !@options.selection
      throw 'Must pass options.documentList, a DocumentList' if 'documentList' not of @options
      throw 'Must pass options.documentDisplayApp, a DocumentDisplay App constructor' if !@options.documentDisplayApp
      throw 'Must pass options.tags, a Collection of Backbone.Tags' if !@options.tags

      @selection = @options.selection
      @documentList = @options.documentList

      @initialRender()

      @documentDisplayApp = new @options.documentDisplayApp({ el: @documentEl })

      @listenTo(@options.tags, 'change', => @renderHeader())
      @listenTo(@selection, 'change:cursorIndex', => @render())
      @setDocumentList(@options.documentList)

    initialRender: ->
      html = @templates.root({ t: t })
      @$el.html(html)
      @$headerEl = @$('header')
      @$documentEl = @$('article')
      @headerEl = @$headerEl[0]
      @documentEl = @$documentEl[0]

      this

    _renderHeader: (maybeDocument) ->
      cursorIndex = @selection.get('cursorIndex')
      nDocuments = @documentList?.get('length') || 0

      tags = @options.tags.filter((x) -> maybeDocument?.hasTag(x))

      html = if !nDocuments || !cursorIndex? || cursorIndex >= nDocuments
        ''
      else
        @templates.header
          nDocuments: nDocuments
          cursorIndex: cursorIndex
          t: t
          tags: tags
          document: maybeDocument
          title: DocumentHelper.title(maybeDocument?.attributes)

      @$headerEl.html(html)

    _getDocument: ->
      cursorIndex = @selection.get('cursorIndex')
      cursorIndex? && @documentList?.documents?.at(cursorIndex) || undefined

    _renderDocument: (maybeDocument) ->
      @documentDisplayApp.setDocument(maybeDocument?.attributes)

    renderHeader: ->
      maybeDocument = @_getDocument()
      @_renderHeader(maybeDocument)

    render: ->
      maybeDocument = @_getDocument()

      @_renderHeader(maybeDocument)

      cursorIndex = @selection.get('cursorIndex')
      @el.className = if cursorIndex?
        if maybeDocument?
          'showing-document'
        else
          'showing-unloaded-document'
      else
        'not-showing-document'

      if maybeDocument?.id != @_lastRenderedDocument?.id
        @_renderDocument(maybeDocument)
        @_lastRenderedDocument = maybeDocument

    _handleNextOrPrevious: (e, nextOrPrevious) ->
      e.preventDefault()

      if !$(e.currentTarget).hasClass('disabled')
        @trigger("#{nextOrPrevious}-clicked")

    _onClickNext: (e) -> @_handleNextOrPrevious(e, 'next')
    _onClickPrevious: (e) -> @_handleNextOrPrevious(e, 'previous')

    _onClickBackToList: (e) ->
      e.preventDefault()
      @trigger("close-clicked")

    setDocumentList: (documentList) ->
      if @documentList?
        @stopListening(@documentList)
        if @documentList.documents?
          @stopListening(@documentList.documents)

      @documentList = documentList

      if @documentList?
        @documentDisplayApp.setSearch(@documentList.params?.params?.q || null)

        @listenTo(@documentList, 'change:length', => @render())
        if @documentList.documents
          @listenTo(@documentList.documents, 'change', => @render())
          @listenTo(@documentList.documents, 'add', => @render())
          @listenTo(@documentList.documents, 'remove', => @render())
          @listenTo(@documentList.documents, 'reset', => @render())

      @render()
