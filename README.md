# ssh-deploy-library

Jenkins Pipeline Library - A Yaml wrapper on top of ssh-steps-plugin.

More about on this [blog](https://engineering.cerner.com/blog/ssh-steps-for-jenkins-pipeline/)

This is just an example library.


Sample YML file:


```yml
config:
  credentials_id: sshUserAcct
  # retry_with_prompt: true
  # retry_and_return: true
  # retry_count: 3

remote_groups:
  r_group_1:
    - name: node01
      host: node01.abc.net
    - name: node02
      host: node02.abc.net
  r_group_2:
    - name: node03
      host: node03.abc.net

command_groups:
  c_group_1:
    - commands:
        - 'ls -lrt'
        - 'whoami'
    - scripts:
        - 'test.sh'
  c_group_2:
    - gets:
        - from: 'test.sh'
          to: 'test_new.sh'
    - puts:
        - from: 'test.sh'
          to: '.'
    - removes:
        - 'test.sh'

steps:
  deploy:
    - remote_groups:
        - r_group_1
      command_groups:
        - c_group_1
    - remote_groups:
        - r_group_2
      command_groups:
        - c_group_2
```
