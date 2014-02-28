// Licensed to Cloudera, Inc. under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  Cloudera, Inc. licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

var AppViewModel = function() {
  var self = this;

  self.station = ko.observable("");
  self.pageTitle = ko.observable("");
  self.focusModel = ko.observable();
  self.cluster = ko.observable("");
  self.cluster.subscribe(function() {
    app.views.tabledata.name('');
  });
  self.clusters = ko.observableArray();
  self.clusterNames = ko.computed(function() {
    return ko.utils.arrayMap(self.clusters(), function (cluster_config) {
      return cluster_config.name;
    });
  });
  API.query('getClusters').done(function(data) {
    app.clusters(data);
  });
  self.search = new tagsearch();

  self.views = {
    tables: new DataTableViewModel({columns:['Table Name', 'Enabled'], el: 'views.tables', reload: function(callback) {
      var d_self = this;
      d_self.items.removeAll();
      API.queryCluster("getTableList").done(function(data) {
        d_self.items.removeAll(); //need to remove again before callback executes
        for(q=0; q<data.length; q++) {
          d_self.items.push(new TableDataRow(data[q]));
        }
        d_self._el.find('a[data-row-selector=true]').jHueRowSelector();
        if(callback!=null)
          callback();
      });
    }}),
    tabledata: new SmartViewModel({el: 'views.tabledata', reload: function(callback) //move inside SmartViewModel class?
    {
      var t_self = this;
      function getColumnFamilies() {
        var cols = [];
        var cfs = t_self.columnFamilies();
        for(var i=0; i<cfs.length; i++) {
          if(cfs[i].enabled()) {
            cols.push(cfs[i].name);
          }
        }
        return cols;
      }
      API.queryTable("getRowQuerySet", JSON.stringify(getColumnFamilies()), ko.toJSON(t_self.querySet())).done(function(data) {
        if(data.length > 0) {
          var keys = Object.keys(data);
          var items = [];
          for(var i=0; i<keys.length; i++) {
            var row = new SmartViewDataRow({items: [], row:data[keys[i]].row, reload: function(options) {
              var self = this;
              options = (options == null) ? {} : options;
              options = ko.utils.extend({
                callback: function(data){},
                columns: getColumnFamilies()
              }, options);
              API.queryTable("getRow", JSON.stringify(options.columns), prepForTransport(self.row)).done(function(data) {
                self.setItems(data.columns);
                callback(data);
                self.isLoading(false);
              });
            }});
            row.setItems(data[keys[i]].columns);
            items.push(row);
          }
          t_self.items(items);
        }
        if(typeof(callback) === 'function')
          callback();
        $('*[data-toggle="tooltip"]').tooltip();
      });
    }})
  };
}

var app = new AppViewModel();

ko.applyBindings(app);


//routing

routed = false;
routie({
    ':cluster/:table/query': function(cluster, table) {
      routie(cluster + '/' + table);
    },
    ':cluster/:table/query/:query': function(cluster, table, query) {
      logGA('query_table');
      $.totalStorage('hbase_cluster', cluster);
      app.station('table');
      app.search.cur_input(query);
      Router.setTable(cluster, table);
      resetElements();
      Views.render('dataview');
      app.views.tabledata._reloadcfs(function(){
        app.search.evaluate();
        app.views.tabledata.searchQuery(query);
      });
      routed = true;
    },
    ':cluster/:table': function(cluster, table) {
      logGA('view_table');
      $.totalStorage('hbase_cluster', cluster);
      Router.setTable(cluster, table);
      resetSearch();
      resetElements();
      app.station('table');
      Views.render('dataview');
      routed = true;
    },
    ':cluster': function(cluster) {
      var redirect = app.clusters.subscribe(function(data) {
        if ($.inArray(cluster, app.clusterNames()) == -1) {
          routie('');
        } else {
          logGA('view_cluster');
          $.totalStorage('hbase_cluster', cluster);
          app.station('cluster');
          app.cluster(cluster);
          app.pageTitle(cluster);
          Views.render('clusterview');
          resetSearch();
          resetElements();
          app.views.tabledata.name('');
          app.views.tables.reload();
          routed = true;
        }
        redirect.dispose();
      });
      resetElements();
      routed = true;
    },
    'error': function() {
      logGA('error');
      routed = true;
    },
    '': function(){
      var redirect = app.clusters.subscribe(function(data) {
        var cluster = $.totalStorage('hbase_cluster');
        if (cluster != null && $.inArray(cluster, app.clusterNames()) > -1) {
          routie(cluster);
        }
        else {
          routie(data[0].name);
        }
        redirect.dispose();
      });
      resetElements();
      routed = true;
    },
    '*': function() {
      logGA('');
      if(!routed)
        history.back();
      routed = false;
    }
});

$.fn.renderElement = function(data){utils.renderElement($(this,data))};

$.fn.showIndicator = function() {
  $(this).addClass('isLoading');
}

$.fn.hideIndicator = function() {
  $(this).removeClass('isLoading');
}

$.fn.toggleIndicator = function() {
  $(this).toggleClass('isLoading');
}

function bindSubmit() {
  var self = this;
  var data = [];
  var hash_cache = {};
  $(this).find('.controls > input, .controls > textarea, .controls > ul input').not('input[type=submit]').each(function() {
    if($(this).hasClass('ignore'))
      return;
    var use_post = $(this).data('use-post');
    var submitVal = null;
    if($(this).data('subscribe')) {
      var target = $($(this).data('subscribe'));
      switch(target[0].tagName) {
        case "UL":
          var serialized = {};
          target.find('li').each(function() {
            serialized[$(this).find('input')[0].value] = $(this).find('input')[1].value;
          });
          submitVal = serialized;
          use_post = true;
          break;
      }
    }
    else if($(this).hasClass('serializeHash')) {
      var target = $(this).attr('name');
      if(!hash_cache[target])
        hash_cache[target] = {};
      hash_cache[target][$(this).data(key)] = $(this).val();
    }
    else {
      submitVal = $(this).val();
      //change reload next
    }
    if(submitVal) {
      if(use_post)
        submitVal = "hbase-post-key-" + JSON.stringify(submitVal);
      else
        submitVal = prepForTransport(submitVal);
      data.push(submitVal);
    }
  });
  $(this).find('input[type=submit]').addClass('disabled').showIndicator();
  var ui = app.focusModel();
  if(ui)
    ui.isLoading(true);
  API.queryArray($(this).attr('action'), data).complete(function() {
    $(self).find('input[type=submit]').removeClass('disabled').hideIndicator();
    if(ui)
      ui.isLoading(false);
  }).success(function() {
    $(self).modal('hide');
    if(ui)
      app.focusModel().reload();
  });
  return false;
}
$('form.ajaxSubmit').submit(bindSubmit).on('hidden', function() {
  $(this).trigger('reset');
});

$('a.action_addColumn').click(function() {
  $(this).parent().find("ul").append("<li><input type=\"text\" name=\"table_columns\" placeholder = \"family_name\"/></li>")
});
