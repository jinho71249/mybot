package androidserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;

import javax.crypto.SecretKey;
import javax.naming.ldap.SortControl;

public class andServer {
	ServerSocket sok ;
    Socket sk;
    String Id;
    public static int getCount() {
		return count;
	}
	public static void setCount(int count) {
		andServer.count = count;
	}
	ArrayList<String> nameInfo;
    ArrayList<String> roomnumber;
    ArrayList<PrintWriter> roomInfo;
    HashMap<String, PrintWriter> hm;
    HashMap<String, Integer> Info;
    static int count=0;
	ArrayList<inputstream> output = new ArrayList<>();
	//ArrayList<Socket> input = new ArrayList<>();
    public andServer() {
	// TODO Auto-generated constructor stub
	 int i=0;
	 hm = new HashMap<>();
	 Info = new HashMap<>();
	 //input.add(sk);
	 nameInfo = new ArrayList<>(); //���������
	 roomInfo = new ArrayList<>(); //�� ����
	 roomnumber = new ArrayList<>(); //�� ��ȣ
	 try {
		sok = new ServerSocket(8000);
		while(true){	
		sk = sok.accept();
		System.out.println("����:"+(++count)+"��");
		inputstream in = new inputstream(sk,hm,Info);
		output.add(in);
		in.start();
		}
		
	 } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
 }
 
class  inputstream  extends Thread
{
	PrintWriter pw=null;
	BufferedReader bf=null;
	private Socket sk;
	private HashMap hm;
	private HashMap Info;
	int counter=0;
	public inputstream(Socket sk,HashMap hm,HashMap Info) {
		this.sk=sk;
		this.hm=hm;	
		this.Info=Info;
	}
	@Override
	public void run() {
		try {
            bf = new BufferedReader(new InputStreamReader(sk.getInputStream(),"UTF-8"));

            pw = new PrintWriter(new OutputStreamWriter(sk.getOutputStream(),"UTF-8"));
                  
            	Id = bf.readLine();
            	System.out.println(Id+"���� �����ϼ̽��ϴ�");
            	
                hm.put(Id,pw); 
            	Info.put("���� ä�ù�", 0);
            	Info.put("���� ä�ù�", 1);
            	Info.put("���͵� ä�ù�", 2);
            	Info.put("���� ä�ù�", 3);
            	Info.put("��� ä�ù�", 4);
            	
				String msg ="";
					
					while(true)
					{
						msg=bf.readLine();
						
						
						if(msg.equals("MSG"))
						{
							pw.println(msg);
							pw.flush();
						}
						
						
						//�濡 ���� ���� ���
						if(msg.contains("RoomIn"))
						{
							String a[] = msg.split("_");
							String b=a[1];
							String c=a[2];
							roommain(b,c);
						}
						
						if(msg.equals("roomInfo"))
						{
							String key="";
							Iterator itr = Info.keySet().iterator();
							while(itr.hasNext())
							{
								key += (String)itr.next()+",";

							}		
									
							SendAll("roomnumber_"+key);
						}
								

						if(msg.contains("roomcreate"))
						{
							String a[] = msg.split("_");
							String b= a[1];
							roomnumber.add(b);
							SendAll("user_"+b);	
						}
						
						SendAll(msg);
						
					}
					
				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			output.remove(this);
		}
	}
}

//����
public void roommain(String Msg,String ma)
{			
	Collection<Integer> a =Info.values();
	int c =Integer.parseInt(Msg);
	for(Integer in :a)
	{
		if(in.equals(c))
		{
			sendMessage(ma);
		}
	}
	
	
}

//��ü ����� ����
public void sendMessage(String Msg)
{		
		
		Iterator iter = Info.keySet().iterator();
		while(iter.hasNext())
		{
			String Key = (String)iter.next();
			
			if(Key.equals(Msg))
			{
			roomInfo.add(hm.get(Key));
			}else break;
		
		}
}
public void SendAll(String Msg)
{
	System.out.println(Msg);
	for(inputstream in:output)
	{
		in.pw.println(Msg);
		in.pw.flush();
	}	
}

//�濡 �ִ� ����鿡�� �޽��� ����
public void Send(String Msg)
{
	System.out.println(Msg);
	for(PrintWriter b : roomInfo)
	{
		b.println(Msg);
		b.flush();
	}
	/*for(int i=0;i<roomInfo.size();i++)
	{
		roomInfo.get(i).println(Msg);
		roomInfo.get(i).flush();
	}*/
}
//����
public void RoomNumber(String Msg)
{
	for(int i=0;i<roomnumber.size();i++)
	{
		if(i==0)
		{
			Send(Msg);
		}else if(i==0)
		{
			Send(Msg);
		}
		
	}
}
 public static void main(String[] args) {
	new andServer();
}

public String getId() {
	return Id;
}

public void setId(String id) {
	Id = id;
}
 
}
