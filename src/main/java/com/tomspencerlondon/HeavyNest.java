package com.tomspencerlondon;

import java.util.HashMap;

class User
{
    public User(String name)
    {
    }

    void setName(String name)
    {
    }
}


public class HeavyNest
{
    HashMap<Integer, User> users = new HashMap<Integer, User>();

    void registerUser(String user)
    {
        String[] parts = user.split(":");

        if (parts.length == 2)
        {
            int userId = Integer.parseInt(parts[0]);
            
            if (userId >= 0)
            {
                String userName = parts[1];

                if (users.containsKey(userId))
                {
                    users.get(userId).setName(userName);
                }
                else
                {
                    users.put(userId, new User(userName));
                }
            }
            else
            {
                throw new IllegalArgumentException("Invalid user id: " + userId);    
            }
        }
        else
        {
            throw new IllegalArgumentException("Invalid user string: " + user);
        }
    }

    void registerUserUnested(String user)
    {
        String[] parts = user.split(":");

        if (parts.length != 2)
        {
            throw new IllegalArgumentException("Invalid user string: " + user);
        }

        int userId = Integer.parseInt(parts[0]);
        
        if (userId < 0)
        {
            throw new IllegalArgumentException("Invalid user id: " + userId);    
        }

        String userName = parts[1];
        if (users.containsKey(userId))
        {
            users.get(userId).setName(userName);
        }
        else
        {
            users.put(userId, new User(userName));
        }
    }

    public static void main(String[] args)
    {
        new HeavyNest().registerUser("23:Russell Chreptyk");
    }
}