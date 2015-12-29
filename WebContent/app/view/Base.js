Ext.define('MyApp.view.Base', {
    extend: 'Ext.container.Viewport',
    id: 'Base',
    layout: {
        type: 'border'
    },
    initComponent: function() {
        var me = this;

        Ext.applyIf(me, {
            items: [ 
			{
				xtype: 'panel',
				layout:'border',
				bodyStyle:"background-image:url('image/head.png')",
				region: 'north',
				flex:1,
				border:false,
				items:[
					{
						xtype:'button',
						region:'east',
						id:'MySpaceButton',
						icon: 'image/goto.png',
                        scale: 'large',
				        tooltip: '进入我的工作区',
				        handler: function(button, event) {
				        	Ext.getCmp('MainContainer').removeAll(false);
				        	Ext.Ajax.request( {  
								url : 'job/getUserRootName',
							    success : function(response, options) {
							    	Ext.getCmp('PublicSpaceButton').setVisible(true);
						        	Ext.getCmp('MySpaceButton').setVisible(false);
							    	var rootname=Ext.decode(response.responseText).toString();
							    	Ext.getCmp('Base').RootName=rootname;
							    	me.refreshWorkspace();
							    },  
							    failure: function(response, opts) {
							    	Ext.getCmp('Base').RootName="root";
					             	Ext.Msg.alert("错误","获取RootName失败");
					            }
							});
				        }
					},
					{
						xtype:'button',
						region:'east',
						id:'PublicSpaceButton',
						icon: 'image/goback.png',
                        scale: 'large',
				        tooltip: '进入公共区',
				        hidden:true,
				        handler: function(button, event) {
				        	Ext.getCmp('MainContainer').removeAll(false);
				        	Ext.getCmp('PublicSpaceButton').setVisible(false);
				        	Ext.getCmp('MySpaceButton').setVisible(true);
				        	Ext.getCmp('Base').RootName='root';
				        	me.refreshWorkspace();
				        }
					}    
				]
			},
			{
				region: 'center',
				id:'Body',
				flex:19,
				layout:'border',
				items:[
				{
					xtype: 'tabpanel',
					id:'EnvTabPanel',
				    flex: 17,
				    region: 'west',
				    listeners:{
				    	'tabchange':function( tabPanel, newCard, oldCard, eOpts ){
				    		Ext.getCmp('MainContainer').removeAll(false);
		    	    		var tabName = newCard.title;
		    	    		switch(tabName)
		    	    		{
			    	    		case "测试配置环境":
			    	    			Ext.getStore('StandardTreeStore').proxy.extraParams.topPath=Ext.getCmp('Base').RootName;
			    	            	Ext.getStore('StandardTreeStore').load();
			    	    			break;
			    	    		case "测试运行环境":
			    	    			Ext.getStore('RunningSet').proxy.extraParams.rootName=Ext.getCmp('Base').RootName;
				            		Ext.getStore('RunningSet').load();
			    	    			break;
		    	    		}
		    	    	}
				    },
				    activeTab:0,
				    items:[
					{
						title:"测试配置环境",
						layout: {
					        type: 'fit'
					    },
						items:[
						{
						    xtype: 'treepanel',
						    id:'tree',
						    title: '文档结构',
						    store: 'StandardTreeStore',
						    rootVisible: true,
						    listeners: {
						        itemmousedown : {
						            fn: me.itemmousedown,
						            scope: me
						        },
						        beforeitemexpand : function ( node, eOpts ){
				                	if(node.childNodes.length==0){
				                		Ext.Ajax.request( {
					                		loadMask: true,
											url : 'job/getTreeChildNodes',
											params : {  
												topPath : Ext.getCmp('Base').RootName,
												node : node.raw.folderName
											},
										    success : function(response, options) {
										    	Ext.get(document.body).unmask(); 
										    	var arr=JSON.parse(response.responseText);
										    	var parts=arr[0].id.split(">");
										    	var id=arr[0].id.replace(">"+parts[parts.length-1],"");
										    	if(id.indexOf(">")>0){
										    		var node=Ext.getStore('StandardTreeStore').getNodeById(id);
											    	for(var i=0;i<arr.length;i++){
							                			node.appendChild(arr[i]);
											    	}
										    	}
										    },
										    failure: function(response, opts) {
										    	Ext.get(document.body).unmask(); 
								             	Ext.Msg.alert("获取直接子节点出错");
								            }
										});
				                	}
				                }
//						        afterrender : {
//						        	fn: me.afterrender,
//						            scope: me
//						        }
						    },
						    viewConfig: {
						        plugins: {
						            ptype: 'treeviewdragdrop'
						        },
						        copy: true,
						        listeners: {
						        	beforedrop : {
						        		fn: me.beforedrop,
						                scope: me
						        	},
						        	drop : {
						        		fn: me.drop,
						                scope: me
						        	},
						        }
						    },
						    dockedItems:[
						    {
						    	xtype:'toolbar',
						    	dock:'top',
						    	autoScroll: true,
						    	items:[
								{
									xtype:'button',
									text:'插入目录',
									handler:function(){
										me.checkLeaf(me.addFolder);
										Ext.getCmp('MainContainer').removeAll(false);
									}
								},
								{
									xtype:'tbseparator'
								},
								{
									//xtype:'button',
									text:'插入测试',
									menu:new Ext.menu.Menu({
		        						plain: true,
		        	                    items:[  
		    	                        {  
		    	                            text:'http(s)',  
		    	                            handler: function(){
		    	                            	Ext.getCmp('Base').IsHttpCase=true;
		    	                            	me.checkLeaf(me.addItem);
												Ext.getCmp('MainContainer').removeAll(false);	
				        					}  
		    	                        },
		    	                        {  
		    	                            text:'socket',  
		    	                            handler: function(){
		    	                            	Ext.getCmp('Base').IsHttpCase=false;
		    	                            	me.checkLeaf(me.addItem);
												Ext.getCmp('MainContainer').removeAll(false);
				        					}
		    	                        }]  
		        	                })
								},	
								{
									xtype:'tbseparator'
								},
								{
									xtype:'button',
									text:'删除节点',
									handler:function(){
										if(Ext.getCmp('tree').getSelectionModel().getSelection()[0].raw.folderName!='root'){
											Ext.widget('DeletedModeWindow').show();
											Ext.getCmp('MainContainer').removeAll(false);
										}else
											Ext.Msg.alert("警告","不能删除root！");
									}    			
								},
								{
									xtype:'tbseparator'
								},					    		
								{
									xtype:'button',
									text:'修改节点',
									handler:function(){
										me.checkNotRoot(me.modifyNode);
										Ext.getCmp('MainContainer').removeAll(false);
									}					    			
								},
								{
									xtype:'tbseparator'
								},					    		
								{
									xtype:'button',
									tooltip: '同一目录下批量复制测试',
									//icon: 'image/copy.png',
						            text: '批量复制',
									handler:function(){
										me.checkIfLeafNode(me.batchedCopyTest);
										Ext.getCmp('MainContainer').removeAll(false);
									}					    			
								}]
						    },
						    {
						    	xtype:'toolbar',
						    	dock:'bottom',
						    	items:[
						    	{
						    		xtype:'radiogroup',
						    		fieldLabel : "查找",
						    		labelWidth: 30,
						    		itemId:'nodeType',
						    		layout: 'hbox',
						    		items:[
									{
										boxLabel: "测试名", name: 'istest', checked:true
									},  
									{
										boxLabel: "目录名", name: 'istest'
									}]
						    	},
						    	{
						    		xtype:'textfield',
						   	    	id:'NodeSearchedText',
						   	    	layout : 'auto',
						   	    	flex:6,
						   	    	emptyText:'输入节点包含文字按回车查找',
						   	    	listeners: {
						                specialkey: function(field, e){
						                	var searchtext=Ext.getCmp('NodeSearchedText').getValue();
						                	var istest=Ext.ComponentQuery.query('#nodeType')[0].items.items[0].checked;
						                    if (e.getKey() == e.ENTER) {
						                    	Ext.Ajax.request( {
													url : 'job/getNodeIdByText',  
													params : { 
														rootName : Ext.getCmp('Base').RootName,
														text : searchtext,
														isTest : istest
													},
												    success : function(response, options) {
												    	var id=Ext.decode(response.responseText);
												    	if(id!=""){
												    		var node=Ext.getStore('StandardTreeStore').getNodeById(id);
												    		Ext.getCmp('tree').expandPath(node.getPath(), 'id','/', function(success, lastNode) {
													    		Ext.getCmp('tree').getSelectionModel().select(node);
													    		Ext.getCmp('tree').fireEvent('itemmousedown',null,node);
													    	});
												    	}else{
												    		var type=istest?"测试":"目录";
												    		Ext.Msg.alert("警告","未找到包含名称'"+searchtext+"'的"+type+"节点！");
												    	}
												    },
												    failure: function(response, opts) {
										             	Ext.Msg.alert("错误","查找节点失败");
										            }
												});
						                    }
						                }
						            }
						    	}]
						    }]
						}]
					},
					{
						title:"测试运行环境",
						layout: {
					        type: 'fit'
					    },
					    items:[
					    {
				            xtype: 'gridpanel',
				            title: '列表',
				            autoFill : true,
				            store: 'RunningSet',
				            stripeRows : true,
				            listeners: {
						        itemmousedown : {
						            fn: me.griditemmousedown,
						            scope: me
						        }
						    },
				            columns: [
							{
							    xtype: 'gridcolumn',
								flex:79,
							    dataIndex: 'name',
							    text: '运行集合',
							},
				            		{
							    xtype: 'gridcolumn',
							    dataIndex: 'time',
							    text: '创建/修改时间',
							    flex: 58,
							    renderer: function(value, metaData, record, rowIndex, colIndex, store, view) {
							    	if(value!=""){
							    		var time=value.split(" ")[1];
							    		value=value.split(" ")[0]+' '+time.substring(0,2)+':'+time.substring(2,4)+':'+time.substring(4,6);
							    	}
							    	return value;
							    }
							},
							{
							    xtype: 'gridcolumn',
							    dataIndex: 'author',
							    text: '作者',
							    flex: 37,
							},
							{
				                xtype: 'actioncolumn',
				                text: '修改',
				                flex:20,
				                items: [
				                {
				                    handler: function(view, rowIndex, colIndex, item, e, record, row) {
				                    	Ext.getCmp('Base').folderName=Ext.getCmp('Base').RootName+'/LAB/'+record.raw.name+'@'+record.raw.time+'@'+record.raw.author;
				                    	Ext.Ajax.request( {
											url : 'job/getSelectedTestPaths',
											params : {  
												foldername : Ext.getCmp('Base').folderName
											},
										    success : function(response, options) {
										    	var json=Ext.decode(response.responseText);
										    	if(json.success){
										    		Ext.getCmp('Base').SelectedTests=json.obj;
										    		Ext.getStore('SelectedTreeStore').proxy.extraParams.rootName=Ext.getCmp('Base').RootName;
										    		Ext.getStore('SelectedTreeStore').proxy.extraParams.testset=Ext.getCmp('Base').SelectedTests;
		        						    		Ext.widget('RunningSetUpdateWindow').show();
										    	}else
										    		Ext.Msg.alert("错误","未找到文件或读文件异常");
										    },
										    failure: function(response, opts) {
										    	Ext.Msg.alert("错误","获取测试集合失败");
								            }
										});
				                    },
				                    icon: 'image/edit.png',
				                    tooltip: 'edit'
				                }]
				            },
				            {
				                xtype: 'actioncolumn',
				                text: '删除',
				                flex:20,
				                items: [
				                {
				                    handler: function(view, rowIndex, colIndex, item, e, record, row) {
				                    	if(Ext.getCmp('Base').RootName.indexOf('root/LAB')>-1){
			                    			Ext.MessageBox.confirm(
						                	"confirm",
						                        "确认删除？",
						                        function(e){
						                            if(e=='yes'){
						                                Ext.getStore('RunningSet').removeAt(rowIndex);
						                                Ext.getStore('RunningSet').proxy.extraParams.rootName=Ext.getCmp('Base').RootName;
						                                Ext.getStore('RunningSet').sync({
						                                	success:function(){
						                                		Ext.getCmp('MainContainer').removeAll(false);
						                                    }
						                                });
						                            }
						                        }); 
		    					}else{
		    						Ext.Msg.alert("警告","请先选中运行集合所在行！");
		    					} 
				                    },
				                    icon: 'image/delete.png',
				                    tooltip: 'delete'
				                }]
				            }],
				            dockedItems: [
				            {
				                xtype: 'toolbar',
				                dock: 'top',
				                items: [
				                {
				                    xtype: 'button',
				                    handler: function(button, event) {
				                    	Ext.getStore('SelectingTreeStore').proxy.extraParams.rootName=Ext.getCmp('Base').RootName;
				                    	Ext.widget('RunningSetSettingWindow').show();
				                    },
				                    icon: 'image/add.png',
				                    tooltip: '新增运行集合'
				                },
				                {
				                    xtype: 'tbseparator'
				                },
				                {
				                    xtype: 'button',
				                    handler: function(button, event) {
				                        Ext.getStore('RunningSet').load();
						    			Ext.getCmp('MainContainer').removeAll(false);
				                    },
				                    icon: 'image/refresh.png',
				                    tooltip: '刷新'
				                }]
				            }]
				        }]
					}]
				},
				{
					xtype:'container',
					flex:50,
				    region: 'center',
				    id:'MainContainer',
				    layout: {
						type: 'fit'
					}
				}
				]
			}]
        });
        me.callParent(arguments);
    },
    beforedrop : function(node, data, overModel, dropPosition, dropHandlers) {
	    dropHandlers.wait = true;
	    Ext.MessageBox.confirm('Drop', '确定要拷贝？', function(btn){
	        if (btn === 'yes') {
	            dropHandlers.processDrop();
	        } else {
	            dropHandlers.cancelDrop();
	        }
	    });
	},
    drop : function(node, oldParent, newParent, index, eOpts ){
		var oldPath=oldParent.records[0].raw.folderName;
		var newPath=newParent.raw.folderName;
		Ext.Ajax.request( {  
			url : 'job/copyNodeWithoutHistory',  
			params : {  
				srcPath : oldPath,
				targetPath : newPath  
			},  
		    success : function(response, options) {
		    	var obj=Ext.decode(response.responseText);
		    	if(!obj.success)
		    		Ext.Msg.alert("错误",obj.msg);
		    },  
		    failure: function(response, opts) {
             	Ext.Msg.alert("错误","拷贝节点失败");
            }
		});
	},	
    itemmousedown:function(view,record, item, index, e, eOpts ){
 		Ext.getCmp('MainContainer').removeAll(false);
 		Ext.getStore('CheckPointResult').loadData([]);
 		if(Ext.String.endsWith(record.raw.folderName,'-leaf')){
 			Ext.getCmp('Base').IsHttpCase=true;
 			Ext.getCmp('Base').folderName=record.raw.folderName;
 			var mainpanel = Ext.widget('MainPanel');
 			Ext.getCmp('MainContainer').add(mainpanel);
 			mainpanel.setTitle(Ext.getCmp('Base').folderName);
 		}else if(Ext.String.endsWith(record.raw.folderName,'-t')){
 			Ext.getCmp('Base').IsHttpCase=false;
 			Ext.getCmp('Base').folderName=record.raw.folderName;
 			var mainpanel = Ext.widget('MainPanel');
 			Ext.getCmp('MainContainer').add(mainpanel);
 			mainpanel.setTitle(Ext.getCmp('Base').folderName);
 		}else if(Ext.String.endsWith(record.raw.folderName,'-dir')){
 			Ext.getCmp('Base').folderName=record.raw.folderName;
 			var panel = Ext.widget('FolderPanel');
 			Ext.getCmp('MainContainer').add(panel);
 			panel.setTitle(Ext.getCmp('Base').folderName);
 		}
 		else{
			var panel = Ext.widget('RootPanel');
//			Ext.Ajax.request( {  
//    			url : 'job/isAdminUser',  
//    		    success : function(response, options) {
//    		    	var isadmin=Boolean(Ext.decode(response.responseText));
//    		    	if(isadmin){
//    		    		var btn=Ext.create('Ext.button.Button',{
//    		    			text:'队列发送',
//        					//icon: 'image/clear.png',
//                            tooltip: '发送全量节点数据到消息队列',
//        					handler:function(){
//        						var lm = new Ext.LoadMask(Ext.getCmp('RootPanel'), { 
//                        			msg : '发送中。。。', 
//                        			removeMask : true
//                        		}); 
//                        		lm.show();
//        						Ext.Ajax.request( {  
//        							url : 'job/pushAllNodesIntoQueue',  
//        						    success : function(response, options) {
//        						    	lm.hide();
//        						    	Ext.Msg.alert("信息","done");
//        						    },  
//        						    failure: function(response, opts) {
//        						    	lm.hide();
//        				             	Ext.Msg.alert("信息","发送途中失败");
//        				            }
//        						});
//        					}
//    		    		});
//    		    		panel.items.items[0].add(btn);
//    		    	}
//    		    },  
//    		    failure: function(response, opts) {
//                }
//    		});
			Ext.getCmp('MainContainer').add(panel);
 		}
 		location.href='#node='+record.raw.folderName;
 	},
 	griditemmousedown:function(view, record, item, index, e, eOpts ){
		Ext.getCmp('MainContainer').removeAll(false);
		 
 		var runningSetFullName=Ext.getCmp('Base').RootName+'/LAB/'+record.raw.name+'@'+record.raw.time+'@'+record.raw.author;
 		Ext.getCmp('Base').folderName=runningSetFullName;
 		
 		var panel = Ext.widget('RunningSetPanel');
 		Ext.getCmp('MainContainer').add(panel);
 		panel.setTitle(record.raw.name);
 		Ext.getCmp('RunningSetStatisticsPanel').add(Ext.widget('TestCaseResultGrid'));
    	Ext.getCmp('RunningSetStatusDistributionPanel').add(Ext.widget('TestStatusDistributionPieChart'));
    	Ext.getCmp('RunningSetPassedRateTrendPanel').add(Ext.widget('TestPassedRateTrendChart'));
    	
 		Ext.getStore('TestCaseResult').proxy.extraParams.dirPath=runningSetFullName;
        Ext.getStore('TestStatusDistribution').proxy.extraParams.dirPath=runningSetFullName;
        Ext.getStore('TestPassedRate').proxy.extraParams.dirPath=runningSetFullName;
        
        Ext.getStore('TestCaseResult').load();
    	Ext.getStore('TestStatusDistribution').load();
    	Ext.getStore('TestPassedRate').load();
 	},
// 	afterrender:function(){
// 		var url=location.href;
// 		if(url.indexOf('#node=')>0 && !new RegExp('#node=$').test(url)){
// 			url=url.substring(url.indexOf('#node=')+6).replace(new RegExp('/','gm'), '>');
// 	 		if(url.substring(url.length-1)=='>')
// 	 			url=url.substring(0, url.length-1);
// 	 		var node=Ext.getStore('StandardTreeStore').getNodeById(url);
// 	 		if(node!=undefined)
// 	 			Ext.getCmp('tree').expandPath(node.getPath(), 'id','/', function(success, lastNode) {
// 	 				Ext.getCmp('tree').getSelectionModel().select(node);
// 	 				Ext.getCmp('tree').fireEvent('itemmousedown',null,node);
// 	 			});	
// 		}
// 	},
 	checkIfLeafNode:function(thefunction){
 		var selection = Ext.getCmp('tree').getSelectionModel().getSelection();
		if(selection.length!=0){
			var themodel=selection[0];
			if(!themodel.get("leaf")){
				Ext.MessageBox.alert("提示","请选择测试节点！");
			}else{
				thefunction(themodel);
			}
		}else{
			Ext.MessageBox.alert("提示","请选择测试节点！");
		}
 	},
 	checkLeaf:function(thefunction){
 		var selection = Ext.getCmp('tree').getSelectionModel().getSelection();
		if(selection.length!=0){
			var themodel=selection[0];
			if(themodel.get("leaf")){
				Ext.MessageBox.alert("提示","请选择目录");
			}else{
				thefunction(themodel);
			}
		}else{
			Ext.MessageBox.alert("提示","请选择目录");
		}
 	},
 	checkNotRoot:function(thefunction){
 		var selection = Ext.getCmp('tree').getSelectionModel().getSelection();
		if(selection.length!=0){
			var themodel=selection[0];
			if(themodel.isRoot()){
				Ext.MessageBox.alert("提示","不能操作根节点");
			}else{
				thefunction(themodel);
			}
		}else{
			Ext.MessageBox.alert("提示","请选择目标");
		}
 	}, 
 	batchedCopyTest:function(themodel){
 		Ext.widget('CopyTestAtSameDirWindow').show();
 		Ext.getCmp('BatchCopyTestForm').theLeafNode=themodel; 	
 	},
 	addFolder:function(themodel){
 		var win=Ext.widget("AddFolderWindow");
 		win.show();
 		Ext.getCmp('AddFolderForm').theNode=themodel; 			
 	},
 	addItem:function(themodel){
 		var win=Ext.widget("AddItemWindow");
 		win.show();
 		Ext.getCmp('AddItemForm').theNode=themodel;
 	},
 	delNode:function(themodel){
 		themodel.remove(false);
	        var thestore=Ext.getStore('StandardTreeStore');
	        var path=themodel.raw.folderName;
	        thestore.proxy.extraParams={
	        	folderName:path
	        };
	        thestore.sync({
	        	success:function(betch,options){
	        		Ext.getCmp('MainContainer').removeAll(false);
	//        		Ext.Ajax.request( {  
	//					url : 'job/subscribleQueue',
	//				    success : function(response, options) {
	//				    },  
	//				    failure: function(response, opts) {
	//		             	console.log("错误","插入队列失败");
	//		            }
	//				});
	        	},
	            failure:function(betch,options){
	            	Ext.Msg.alert("错误","删除节点失败");
	            	thestore.load();
	            }
	        });
 	},
 	modifyNode:function(themodel){
 		Ext.getCmp('Base').theNode=themodel;
 		Ext.widget("ModifyNodeWindow").show();
 	},
 	saveEvn:function(){
 		Ext.Ajax.request({
         	url:'job/saveEnvironment',
         	params: {
         		folderName: Ext.getCmp('Base').folderName,
         		env:Ext.getCmp('EnvEditForm').getForm().findField('var').value
         		} ,
            success: function(response, opts) {
            	Ext.getCmp('EnvEditWindow').close();
            },
             failure: function(response, opts) {
             	Ext.Msg.alert("错误","请求失败");
            }
         });
 	},
 	showEvn:function(){
 		Ext.Ajax.request({
         	url:'job/showEnvironment',
         	params: { folderName: Ext.getCmp('Base').folderName } ,
            success: function(response, opts) {
            	Ext.getCmp('EnvEditForm').getForm().findField('var').setValue(Ext.decode(response.responseText).obj);
            },
             failure: function(response, opts) {
             	Ext.Msg.alert("错误","请求失败");
             	Ext.getCmp('EnvEditForm').getForm().findField('var').setValue("请求失败");
            }
         });
 	},
	PayAction: function (auth, onum, title, amount,callbackhost,paymentdomain,busttype) { 
		//需要配置出来的东西 
		//callbackhost
		//paymentdomain
			//var callbackhost="m.ctrip.com";
			//var paymentdomain="https://secure.ctrip.com";
            var sback, pback; 
            var host = "http://" + callbackhost; 
            sback = "/webapp/lipin/#booking.success?onum=" + onum; 
            pback = "/webapp/lipin/#order.detail?onum=" + onum; 
            pback = encodeURIComponent(host + pback); 
            sback = encodeURIComponent(host + sback); 
            var tokenJson = { 
                oid: onum, 
                bustype: busttype, 
                pback: pback, 
                sback: sback, 
                auth: auth, 
                title: title, 
                amount: amount 
            }; 
            if (onum != "") { 
                //bustype:业务类型token:身份验证数据sback:支付成功回调页面oid:订单号pback:订单详情回调地址 
                tokenJson = encodeURIComponent(this.Base64Enocde(JSON.stringify(tokenJson))); 
                //tokenJson = tokenJson.toString(); 
                var QueryString = ["bustype="+busttype+"&oid=", onum, "&token=", tokenJson].join(""); 
                window.open( paymentdomain + "/webapp/payment/index.html#index?" + QueryString); 
                //this.jump(); 
            } 
     },
	Base64Enocde:function(input) {  
    	var _keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";  
        var output = "";  
        var chr1, chr2, chr3, enc1, enc2, enc3, enc4;  
        var i = 0;  
        input = this._utf8_encode(input);  
        while (i < input.length) {  
            chr1 = input.charCodeAt(i++);  
            chr2 = input.charCodeAt(i++);  
            chr3 = input.charCodeAt(i++);  
            enc1 = chr1 >> 2;  
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);  
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);  
            enc4 = chr3 & 63;  
            if (isNaN(chr2)) {  
                enc3 = enc4 = 64;  
            } else if (isNaN(chr3)) {  
                enc4 = 64;  
            }  
            output = output +  
            _keyStr.charAt(enc1) + _keyStr.charAt(enc2) +  
            _keyStr.charAt(enc3) + _keyStr.charAt(enc4);  
        }  
        return output;  
    } , 
    _utf8_encode: function (string) {  
        string = string.replace(/\r\n/g,"\n");  
        var utftext = "";  
        for (var n = 0; n < string.length; n++) {  
            var c = string.charCodeAt(n);  
            if (c < 128) {  
                utftext += String.fromCharCode(c);  
            } else if((c > 127) && (c < 2048)) {  
                utftext += String.fromCharCode((c >> 6) | 192);  
                utftext += String.fromCharCode((c & 63) | 128);  
            } else {  
                utftext += String.fromCharCode((c >> 12) | 224);  
                utftext += String.fromCharCode(((c >> 6) & 63) | 128);  
                utftext += String.fromCharCode((c & 63) | 128);  
            }  
        }  
        return utftext;  
    },
    refreshWorkspace: function(){
    	var activetab=Ext.getCmp('EnvTabPanel').getActiveTab();
    	if(activetab.title=="测试配置环境"){
    		Ext.getStore('StandardTreeStore').proxy.extraParams.topPath=Ext.getCmp('Base').RootName;
        	Ext.getStore('StandardTreeStore').load();
    	}else if(activetab.title=="测试运行环境"){
    		Ext.getStore('RunningSet').proxy.extraParams.rootName=Ext.getCmp('Base').RootName;
    		Ext.getStore('RunningSet').load();
    	}
    }
});
