import struct,gzip,json,sys,pathlib
from io import BytesIO
class Tag:
 def __init__(self,t,v): self.t=t;self.v=v
 def plain(self):
  if self.t==10:return {k:v.plain() for k,v in self.v.items()}
  if self.t==9:return [x.plain() for x in self.v[1]]
  return self.v
 def __repr__(self):return repr(self.plain())
def load(path):
 f=BytesIO(gzip.decompress(pathlib.Path(path).read_bytes()))
 def read(fmt): return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
 def string():return f.read(read('H')).decode()
 def value(t):
  if t in [1,2,3,4,5,6]:return Tag(t,read({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t]))
  if t in [7,11,12]:return Tag(t,[read({7:'b',11:'i',12:'q'}[t]) for _ in range(read('i'))])
  if t==8:return Tag(t,string())
  if t==9:
   tt=read('b');n=read('i');return Tag(t,(tt,[value(tt) for _ in range(n)]))
  if t==10:
   d={}
   while (tt:=read('b')):key=string();d[key]=value(tt)
   return Tag(t,d)
  raise ValueError(t)
 t=read('b');name=string();return name,value(t)
def save(path,name,root):
 f=BytesIO()
 def write(fmt,v):f.write(struct.pack('>'+fmt,v))
 def string(s):b=s.encode();write('H',len(b));f.write(b)
 def value(tag):
  t,v=tag.t,tag.v
  if t in [1,2,3,4,5,6]:write({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
  elif t in [7,11,12]:
   write('i',len(v))
   for x in v:write({7:'b',11:'i',12:'q'}[t],x)
  elif t==8:string(v)
  elif t==9:
   write('b',v[0]);write('i',len(v[1]))
   for x in v[1]:value(x)
  elif t==10:
   for k,x in v.items():write('b',x.t);string(k);value(x)
   write('b',0)
 write('b',root.t);string(name);value(root)
 pathlib.Path(path).write_bytes(gzip.compress(f.getvalue(),mtime=0))
if __name__=='__main__':
 n,t=load(sys.argv[1]);print(json.dumps(t.plain(),indent=2))
