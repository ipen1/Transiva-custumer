import json, urllib.request, urllib.error, subprocess
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import os

uuid='11111111-1111-4111-8111-111111111111'
tokens={'merchant':'merchant-fixture-token','admin':'admin-fixture-token','customer':'customer-fixture-token','driver':'driver-fixture-token','other-driver':'other-driver-token','other-customer':'other-customer-token'}
origin={'lat':-.9,'lng':119.9}
def request(file, body=None, actor='customer', headers=None):
    h={'Content-Type':'application/json','X-App-Scope':actor.split('-')[-1],'X-Device-UUID':uuid}
    if actor: h['Authorization']='Bearer '+tokens[actor]
    if headers: h.update(headers)
    req=urllib.request.Request('http://127.0.0.1:18349/'+file,None if body is None else json.dumps(body).encode(),h)
    try:
        r=urllib.request.urlopen(req,timeout=12)
    except urllib.error.HTTPError as err:
        r=err
    raw=r.read()
    try:
        return r.status,json.loads(raw)
    except ValueError:
        raise AssertionError((r.status, raw[:900]))

def action(kind,id='',actor='customer',**attrs):
    return request('transsend_contact.php',dict(action=kind,contact_id=id,**attrs),actor)

def driver_list(actor='driver'):
    code,data=action('list',actor=actor)
    assert code==200,data
    return data['contacts']

def db(sql):
    client=os.environ["TRANSIVA_TEST_MYSQL"]
    return subprocess.check_output([client,'--no-defaults','-h127.0.0.1','--port='+os.environ.get('TRANSIVA_TEST_DB_PORT','3306'),'-uroot','-NB','courier_test','-e',sql],text=True).strip()





location={**origin,'service':'trans_simpel'}
a={'lat':-.9,'lng':119.9,'address':'Jemput'}
b={'lat':-.92,'lng':119.92,'address':'Antar'}
def invite(brief):
    return action('invite',driver_id=2,**location,tiva=brief)
def route(gid,act,actor='customer',**options):
    return request('trans_simpel.php',{'action':act,'contact_id':gid,**options},actor)
food={'key':'4db0d75e-3164-4d14-8782-e7e7e71a9df0','kind':'food','restaurant_id':111,'menu_id':1,'quantity':2,'delivery':b,'topping':'Keju'}
db("INSERT INTO food_menus(id,restaurant_id,name,price,is_active) VALUES (5,111,'Es Teh',8000,1)")
db("""INSERT INTO merchant_menu_meta(menu_id,restaurant_id,track_stock,stock,options_json) VALUES (5,111,1,3,'[{"type":"variant","label":"Ukuran","items":[{"name":"Besar","price":2000}]},{"type":"topping","label":"Ekstra","items":[{"name":"Lemon","price":1000}]}]')""")
multi={**food,'food_items':[{'id':1,'qty':2,'selected_options':[{'type':'bundle_topping','name':'Keju'}]},
        {'id':5,'qty':1,'selected_options':[{'type':'variant','name':'Besar'},{'type':'topping','name':'Lemon'}]}]}
assert invite({**multi,'food_items':multi['food_items']+[{'id':1,'qty':3,'selected_options':multi['food_items'][0]['selected_options']}]})[0]==409
assert invite({**multi,'food_items':[{'id':5,'qty':1,'selected_options':[{'type':'variant','name':'Palsu'}]}]})[0]==422
db("UPDATE food_restaurants SET banner='uploads/restaurants/rosto.png' WHERE id=111")
code,catalog=request('tiva_simpel_catalog.php',origin)
assert code==200 and catalog['stores'][0]['image']=='uploads/restaurants/rosto.png',(code,catalog)
code,catalog=request('tiva_simpel_catalog.php',{**origin,'restaurant_id':111})
assert code==200 and all(m['image'] and m['price']>0 for m in catalog['menus']),(code,catalog)
print('PASS restaurant photo and menu photo/price are returned for Tiva')
preview={'restaurant_id':111,'food_items':multi['food_items'],'origin':{**origin,'address':'Sekitar'},
         'delivery':b}
before_preview=db("SELECT COUNT(*) FROM orders")
code,estimate=request('create_food_order.php',{'quote_only':True,'tiva_preview':preview})
assert code==200 and estimate.get('success'),(code,estimate)
totals=estimate['quote']
assert totals['food_total']>0 and totals['delivery_fee']>0 and totals['total']==totals['food_total']+totals['delivery_fee']
assert db("SELECT COUNT(*) FROM orders")==before_preview
code,blocked=request('create_food_order.php',{'id':'invalid-prequote-write','tiva_preview':preview})
assert code==422 and not blocked.get('success'),(code,blocked)
code,invalid=request('create_food_order.php',{'quote_only':True,'tiva_preview':{**preview,'food_items':[]}})
assert code==422 and not invalid.get('success'),(code,invalid)
print('PASS Tiva pre-contact draft quotes server subtotal/fee/total and cannot create an order')
status,g=invite(multi)
assert status==200,(status,g)
gid=g['contact']['id']
assert db("SELECT COUNT(*) FROM merchant_order_lookup")=='0'
code,contacts=action('list',actor='driver')
assert any(x['id']==gid and x['state']=='pending' and x['service']=='trans_simpel' for x in contacts['contacts']),contacts
assert action('accept',gid,actor='driver')[0]==200
assert route(gid,'quote')[0]==409
checkout={'tiva_contact_id':gid,'revision':1,'payment_method':'cash'}
code,q=request('create_food_order.php',{**checkout,'quote_only':True})
assert code==200 and isinstance(q.get('route',{}).get('total'),int),(code,q)
total=q['route']['total']
assert total>=40000,total
assert total==totals['total'] and q['quote']['delivery_fee']==totals['delivery_fee'],q
code,o=request('create_food_order.php',{**checkout,'id':'tiva-food-case','total':total})
assert code==200 and o.get('status')=='driver_accepted',(code,o)
assert db("SELECT COUNT(*) FROM merchant_order_lookup")=='1'
assert db("SELECT COUNT(*) FROM order_notification_outbox WHERE event_key LIKE 'food:%:merchant:%'")=='1'
items=db("SELECT JSON_UNQUOTE(JSON_EXTRACT(note,'$.items[0].selected_options[0].name')) FROM orders WHERE id="+str(o['order_db_id']))
assert items=='Keju',items
assert db("SELECT JSON_UNQUOTE(JSON_EXTRACT(note,'$.items[1].selected_options[1].name')) FROM orders WHERE id="+str(o['order_db_id']))=='Lemon'
assert db("SELECT stock FROM merchant_menu_meta WHERE menu_id=5")=='2'
print('PASS selected driver, real TransFood menu and topping, combined total and merchant publication')
db("UPDATE orders SET status='finished' WHERE id="+str(o['order_db_id'])+"; UPDATE driver_profiles SET is_busy=0 WHERE user_id=2")
normal={'id':'normal-food-case','restaurant_id':111,'items':[{'id':1,'qty':1,'selected_options':[{'type':'bundle_topping','name':'Keju'}]}],'payment_method':'cash'}
code,n=request('create_food_order.php',normal)
assert code==200 and n.get('status')=='offered',(code,n)
nid=n['order_db_id']
assert db("SELECT COUNT(*) FROM merchant_order_lookup WHERE order_db_id="+str(nid))=='0'
code,incoming=request('getMerchantOrders.php?outlets=all&scope=incoming',actor='merchant')
assert code==200 and incoming.get('scope')=='incoming' and len(incoming['orders'])<=3,(code,incoming)
assert all(int(x['id'])!=nid for x in incoming['orders'])
code,mer=request('getMerchantOrders.php?scope=active',actor='merchant')
assert code==200 and all(int(x['id'])!=nid for x in mer.get('orders',[])),(code,mer)
assert db("SELECT COUNT(*) FROM order_notification_outbox WHERE event_key LIKE 'food:%:merchant:%'")=='1'
db("UPDATE orders SET status='finished' WHERE id="+str(o['order_db_id'])+"; UPDATE driver_profiles SET is_busy=0 WHERE user_id=2")
status,search=request('customer_food_order_status.php',{'order_id':n['order_id']})
assert status==200 and search.get('status')=='offered',(status,search)
status,search=request('check_order_status.php',{'order_id':n['order_id']})
assert status==200 and search.get('success') and search.get('status')=='offered',(status,search)
assert int(db("SELECT COUNT(*) FROM driver_dispatch_offers WHERE order_id="+str(nid)+" AND driver_username='anna_private' AND outcome='offered'"))==1
code,r=request('driver_accept_order_native.php',{'id':nid,'source':'orders'},actor='driver')
assert code==200 and r.get('success'),(code,r)
assert db("SELECT COUNT(*) FROM merchant_order_lookup WHERE order_db_id="+str(nid))=='1'
code,mer=request('getMerchantOrders.php?scope=active',actor='merchant')
received=[x for x in mer.get('orders',[]) if int(x['id'])==nid]
assert code==200 and received and received[0]['status']=='pending' and received[0]['merchant_response_epoch']>0,(code,mer)
code,incoming=request('getMerchantOrders.php?outlets=all&scope=incoming',actor='merchant')
assert code==200 and incoming.get('scope')=='incoming' and len(incoming['orders'])<=3,(code,incoming)
assert any(int(x['id'])==nid and x['status']=='pending' for x in incoming['orders'])
assert request('getMerchantOrders.php?outlets=all&scope=incoming&outlet_id=112',actor='merchant')[0]==403
db("INSERT INTO native_api_tokens VALUES (29,102,'merchant',NULL,NULL,SHA2('second-merchant-fixture-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL)")
tokens['other-merchant']='second-merchant-fixture-token'
code,other=request('getMerchantOrders.php?outlets=all&scope=incoming',actor='other-merchant')
assert code==200 and all(int(row['id'])!=nid for row in other['orders']),(code,other)
card=next(row for row in incoming['orders'] if int(row['id'])==nid)
assert card['restaurant_id']==111 and card['items'][0]['selected_options'][0]['name']=='Keju',card
print('PASS incoming cards use the correct branch, include toppings, and exclude unrelated merchants')

http,arrived=request('driverArrivedPickup.php',{'id':nid,'driver_lat':a['lat'],'driver_lng':a['lng']},actor='driver')
assert http==200 and arrived.get('success'),(http,arrived)
assert db(f"SELECT COUNT(*) FROM smart_waiting_sessions WHERE source='orders' AND order_id={nid}")== '0'
# Simulate a pickup session created by the old release. The first six minutes are pre-acceptance.
db(f"INSERT INTO smart_waiting_sessions(source,order_id,point_key,label,started_at,free_seconds,rate_per_minute) VALUES('orders',{nid},'pickup','Penjemputan',UNIX_TIMESTAMP()-360,300,500)")
code,tracking=request('check_order_status.php',{'order_id':n['order_id']})
assert code==200 and tracking.get('success') and tracking['order']['smart_waiting']['active'] is None,(code,tracking)
db(f"DELETE FROM smart_waiting_sessions WHERE source='orders' AND order_id={nid}")
_,early=request('driverStartDelivery.php',{'id':nid},actor='driver')
assert not early.get('success'),early
import base64
from urllib.parse import quote
room=db(f"SELECT room_id FROM orders WHERE id={nid}")
assert room.startswith('ROOM-'),room
media_name=f'customer_{room}_20261122_120000_deadbeef.png'
photo_file=Path(__file__).parent/'web/uploads/chat/preview'/media_name
photo_file.parent.mkdir(parents=True,exist_ok=True)
photo=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/bqkAAAAASUVORK5CYII=')
photo_file.write_bytes(photo)
relative='view_chat_image.php?file='+quote('preview/'+media_name,safe='')
head={'X-App-Scope':'driver','Authorization':'Bearer '+tokens['driver'],'X-Device-UUID':uuid}
with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:18349/'+relative,headers=head),timeout=10) as r:
    assert r.getheader('Content-Type')=='image/png' and r.read()==photo
code,denied=request(relative,actor='other-driver')
assert code in (403,404) and not denied['success'],(code,denied)
print('PASS protected photo endpoint serves the active driver only')
status,decision=request('updateMerchantOrder.php',{'id':nid,'restaurant_id':111,'status':'merchant_accepted'},actor='merchant')
assert status==200 and decision.get('success') and decision.get('global_status')=='arrived_pickup',(status,decision)
code,incoming=request('getMerchantOrders.php?outlets=all&scope=incoming',actor='merchant')
assert code==200 and incoming.get('scope')=='incoming' and len(incoming['orders'])<=3,(code,incoming)
assert all(int(x['id'])!=nid for x in incoming['orders'])
code,tracking=request('check_order_status.php',{'order_id':n['order_id']})
assert code==200 and tracking.get('success') and tracking['order']['smart_waiting']['active']['elapsed_seconds']<10,tracking
assert db(f"SELECT COUNT(*) FROM smart_waiting_sessions WHERE source='orders' AND order_id={nid}")=='1'
db(f"UPDATE smart_waiting_sessions SET started_at=UNIX_TIMESTAMP()-360 WHERE source='orders' AND order_id={nid}")
code,tracking=request('check_order_status.php',{'order_id':n['order_id']})
assert tracking['order']['smart_waiting']['total_fee']==0,tracking
print('PASS pickup waiting ignores pre-acceptance time including older sessions')
http,ready=request('updateMerchantOrder.php',{'id':nid,'restaurant_id':111,'status':'ready'},actor='merchant')
assert http==200 and ready.get('success'),(http,ready)
http,started=request('driverStartDelivery.php',{'id':nid},actor='driver')
assert http==200 and started.get('success'),(http,started)
print('PASS real driver dispatch, merchant visibility gate, one-tap acceptance, pickup/merchant status isolation')
db("UPDATE orders SET status='finished' WHERE id="+str(nid)+"; UPDATE driver_profiles SET is_busy=0 WHERE user_id=2")
for number,kind,service in [(1,'ride','Transbike'),(2,'shop','TransShop')]:
    draft={'key':'4db0d75e-3164-4d14-8782-e7e7e71a9df'+str(number),'kind':kind,'pickup':a,'delivery':b,'detail':'Roti'}
    preview_payload=dict(origin=origin,pickup=a,delivery=b,kind=kind)
    assert route('','preview_route',actor='driver',**preview_payload)[0]==403
    count=db("SELECT COUNT(*) FROM transsend_contacts")
    code,preview=route('','preview_route',**preview_payload)
    assert code==200 and preview['quote']['total']>0,(code,preview)
    assert db("SELECT COUNT(*) FROM transsend_contacts")==count
    code,g=invite(draft)
    assert code==200,(code,g)
    gid=g['contact']['id']
    assert action('active_list',actor='driver')[0]==403
    code,active=action('active_list')
    assert code==200 and active['contacts'][0]['id']==gid and active['contacts'][0]['state']=='pending',(code,active)
    assert action('active_list',actor='other-customer')[1]['contacts']==[]
    assert action('accept',gid,actor='driver')[0]==200
    assert action('active_list')[1]['contacts'][0]['room_id']=='ROOM-'+gid
    revision=1
    if kind=='ride':
        _,r=route(gid,'get')
        initial=r['route']
        assert initial==route(gid,'get',actor='driver')[1]['route']
        v=initial['revision']
        pa={'lat':-.904,'lng':119.903,'address':'Jemput baru'}
        pb={'lat':-.923,'lng':119.923,'address':'Antar baru'}
        assert route(gid,'set',point='pickup',revision=v,previous=initial['pickup'],location=pa)[0]==200
        assert route(gid,'set',actor='driver',point='delivery',revision=v,previous=initial['delivery'],location=pb)[0]==200
        assert route(gid,'set',actor='driver',point='pickup',revision=v,previous=initial['pickup'],location=a)[0]==409
        _,r=route(gid,'get')
        latest=r['route']
        assert latest==route(gid,'get',actor='driver')[1]['route']
        assert latest['pickup']==pa and latest['delivery']==pb,latest
        revision=latest['revision']
        print('PASS customer and driver see the same revised coordinates; stale edits fail safely')
    code,q=route(gid,'quote')
    assert code==200,(code,q)
    code,result=route(gid,'order',revision=revision,total=q['route']['total'])
    assert code==200,(code,result)
    assert db("SELECT order_type FROM orders WHERE order_id='"+result['order_id']+"'")==service
    code,active=action('active_list')
    assert code==200,(code,active)
    item=next(o for o in active['orders'] if o['order_id']==result['order_id'])
    assert item['can_cancel'] and item['chat']['room_id']=='ROOM-'+gid,item
    if kind=='ride':
        order_id=result['order_id']
        body={'room_id':'ROOM-'+gid,'order_id':gid,'source':'pickup_orders','message':'Pesan privat lama'}
        official_body={**body,'room_id':'ROOM-'+order_id,'order_id':order_id,'source':'orders'}
        code,sent=request('sendChat.php',official_body)
        assert code==200 and sent.get('success'),(code,sent)
        db(f"UPDATE orders SET driver='budi_private' WHERE order_id='{order_id}'")
        code,reassigned=action('active_list')
        current=next(o for o in reassigned['orders'] if o['order_id']==order_id)
        assert code==200 and current['chat']['order_id']==order_id and current['chat']['source']=='orders',(code,current)
        assert current['previous_chat']['room_id']=='ROOM-'+gid,current
        fresh='getChat.php?order_id='+order_id+'&source=orders&chat_ui=1'
        private='getChat.php?order_id='+gid+'&room_id=ROOM-'+gid+'&source=pickup_orders&chat_ui=1'
        code,fresh_chat=request(fresh,actor='other-driver')
        assert code==200 and not fresh_chat['messages'],(code,fresh_chat)
        assigned_room=fresh_chat['room_id']
        assert assigned_room!=('ROOM-'+gid),fresh_chat
        code,sent=request('sendChat.php',{**official_body,'room_id':assigned_room,'message':'Balasan driver pengganti'},actor='other-driver')
        assert code==200 and sent.get('success'),(code,sent)
        assert request('getChat.php?room_id='+assigned_room,actor='other-driver')[0]==200
        assert request(private,actor='other-driver')[0]==403
        db(f"UPDATE orders SET driver='third_fixture' WHERE order_id='{order_id}'")
        code,fresh_chat=request(fresh)
        assert code==200 and fresh_chat['room_id']!=assigned_room and not fresh_chat['messages'],(code,fresh_chat)
        assert request('getChat.php?room_id='+assigned_room,actor='customer')[0]==404
        code,old_chat=request(private)
        assert code==200 and old_chat['ended'] and any(m['message']=='Pesan privat lama' for m in old_chat['messages']),(code,old_chat)
        assert request('sendChat.php',body)[0]==409
        print('PASS current assignment chat and private read-only history after driver reassignment')
        db(f"UPDATE orders SET driver='anna_private' WHERE order_id='{order_id}'")
    assert action('active_list',actor='other-customer')[1]['orders']==[]
    payload=dict(order_id=result['order_id'],table='orders',trans_simpel_list=True)
    code,denied=request('cancel_order.php',payload,actor='other-customer')
    assert denied['success'] is False,(code,denied)
    if kind=='ride':
        code,cancelled=request('cancel_order.php',payload)
        assert code==200 and cancelled['success'],(code,cancelled)
        assert db("SELECT is_busy FROM driver_profiles WHERE user_id=2")=='0'
        assert all(o['order_id']!=result['order_id'] for o in action('active_list')[1]['orders'])
    else:
        assert q['route']['total']==preview['quote']['total'],(q,preview)
        db("UPDATE orders SET status='on_delivery' WHERE order_id='"+result['order_id']+"'")
        assert not next(o for o in action('active_list')[1]['orders'] if o['order_id']==result['order_id'])['can_cancel']
        assert request('cancel_order.php',payload)[0]==409
        db("UPDATE orders SET status='finished' WHERE order_id='"+result['order_id']+"'; UPDATE driver_profiles SET is_busy=0 WHERE user_id=2")
print('PASS ride and shop pricing previews, active list ownership, and pre-departure cancellation')

draft={'key':'4db0d75e-3164-4d14-8782-e7e7e71a9df9','kind':'send','pickup':a,'delivery':b,'detail':'Buku kantor'}
code,g=invite(draft)
assert code==200,(code,g)
gid=g['contact']['id']
assert action('accept',gid,actor='driver')[0]==200
assert route(gid,'quote')[0]==409
url='calculatePickupOngkir.php?pickup_lat=-0.9&pickup_lng=119.9&delivery_lat=-0.92&delivery_lng=119.92'
code,q=request(url)
assert code==200 and q['success'],(code,q)
payload={'id':'tiva-send-case','courier_contact_id':gid,'item_name':'Buku kantor','receiver_name':'Customer','receiver_phone':'0811111111',
    'pickup_address':'Jemput','delivery_address':'Antar','pickup_lat':a['lat'],'pickup_lng':a['lng'],'delivery_lat':b['lat'],'delivery_lng':b['lng'],
    'approved_price':q['price'],'payment_method':'cash','package_size':'small'}
code,send=request('create_pickup_order.php',payload)
assert code==200 and send.get('status')=='taken',(code,send)
assert db("SELECT CONCAT(status,':',CHAR_LENGTH(delivery_otp)) FROM pickup_orders WHERE order_id='"+send['order_id']+"'")=='taken:6'
entry=next(o for o in action('active_list')[1]['orders'] if o['order_id']==send['order_id'])
assert entry['source']=='pickup_orders' and entry['can_cancel'],entry
send_payload=dict(order_id=send['order_id'],table='pickup_orders',trans_simpel_list=True)
code,cancelled=request('cancel_order.php',send_payload)
assert code==200 and cancelled['success'],(code,cancelled)
assert request('cancel_order.php',send_payload)[1]['already_canceled']
assert all(o['order_id']!=send['order_id'] for o in action('active_list')[1]['orders'])
print('PASS Tiva TransSend uses standard package confirmation and OTP')

db("UPDATE driver_profiles SET is_online=0")
code,waiting=request('create_food_order.php',{**normal,'id':'regular-food-redispatch','items':[{'id':5,'qty':1}]})
assert code==200 and waiting.get('status')=='offered',(code,waiting)
wid=waiting['order_db_id']
assert db(f"SELECT COUNT(*) FROM driver_dispatch_offers WHERE order_id={wid} AND source_table='orders'")=='0'
db("UPDATE driver_profiles SET is_online=1,is_busy=0,last_location_at=NOW() WHERE user_id=3")
code,tracking=request('check_order_status.php',{'order_id':waiting['order_id']})
assert code==200 and tracking.get('success'),(code,tracking)
assert db(f"SELECT COUNT(*) FROM driver_dispatch_offers WHERE order_id={wid} AND source_table='orders' AND driver_username='budi_private' AND outcome='offered'")=='1'
print('PASS search-screen heartbeat produces an offer when a driver comes online')
db(f"UPDATE orders SET status='cancelled' WHERE id={wid}")

def run_worker(op):
    return subprocess.check_output([os.environ['TRANSIVA_TEST_PHP'],
        str(Path(__file__).with_name('worker.php')),op],text=True).strip()

db("UPDATE driver_profiles SET is_online=0")
code,waiting=request('create_food_order.php',{**normal,'id':'food-background-expiry','items':[{'id':5,'qty':1}]})
assert code==200 and waiting.get('status')=='offered',(code,waiting)
wid=waiting['order_db_id']
stock=db("SELECT stock FROM merchant_menu_meta WHERE menu_id=5")
db(f"UPDATE orders SET created_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE id={wid}")
affected=run_worker('expire')
assert int(affected)>=1,affected
assert db(f"SELECT status FROM orders WHERE id={wid}") in ('canceled','cancelled')
assert int(db("SELECT stock FROM merchant_menu_meta WHERE menu_id=5"))==int(stock)+1
print('PASS background expiry cancels unassigned food after deadline and releases stock')

db("UPDATE driver_profiles SET is_online=0")
code,waiting=request('create_food_order.php',{**normal,'id':'food-background-dispatch','items':[{'id':5,'qty':1}]})
assert code==200 and waiting.get('status')=='offered',(code,waiting)
wid=waiting['order_db_id']
db("UPDATE driver_dispatch_offers SET outcome='expired',expired_at=DATE_SUB(NOW(),INTERVAL 5 SECOND) WHERE driver_username='budi_private' AND outcome='offered'; UPDATE driver_profiles SET is_online=1,is_busy=0,last_location_at=NOW() WHERE user_id=3")
result=json.loads(run_worker('dispatch'))
assert any(x.get('id')==wid and x.get('assigned') for x in result),result
print('PASS server dispatch scan creates offer without customer status request')


# Return the already claimed fixture order to just-before-merchant-acceptance with an expired server clock.
db(f"""UPDATE orders SET status='arrived_pickup',note=JSON_SET(
    JSON_REMOVE(note,'$.merchant_accepted_at','$.merchant_accepted_epoch','$.merchant_preparing_at','$.merchant_ready_at'),
    '$.merchant_status','pending','$.merchant_visible_epoch',UNIX_TIMESTAMP()-301) WHERE id={nid}""")
code,decision=request('updateMerchantOrder.php',{'id':nid,'restaurant_id':111,'status':'merchant_accepted',
    'idempotency_key':'fixture-deadline-late-merchant'},actor='merchant')
assert not decision['success'] and decision['code']=='MERCHANT_RESPONSE_TIMEOUT',(code,decision)
assert db(f"SELECT status FROM orders WHERE id={nid}")=='merchant_rejected'
print('PASS late merchant response is rejected without advancing driver status')
