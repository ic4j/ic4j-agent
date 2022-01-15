/*
 * Copyright 2021 Exilor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.ic4j.agent;


public final class Waiter{
	int timeout;
	int sleep;
	int waited = 0;
	
	Waiter(int timeout,int sleep)
	{
		this.timeout = timeout;
		this.sleep = sleep;
	}
	
	public static Waiter create(int timeout, int sleep)
	{
		return new Waiter(timeout,sleep);
	}
	
	boolean waitUntil() 
	{
		if(timeout > 0 && waited >= timeout)
			return false;
		
		try
		{
			Thread.sleep(sleep*1000);
		}
		catch(InterruptedException e)
		{
			
		}
		
		waited += sleep;
		
		return true;
	}
}
