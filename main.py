import os
import re

def define_env(env):
    "Hook function"

    @env.macro
    def insert_content(key = None):
        key = key or env.page.title
        filename = env.variables['extra']['additional_content'][key]
        return include_file(filename)

    @env.macro
    def include_file(filename):
        prefix = env.variables['config']['docs_dir']
        full_filename = os.path.join(prefix, filename)
        with open(full_filename, 'r') as f:
            lines = f.readlines()
        return ''.join(lines)


    """ 
    def copy_markdown_images(tmpRoot, markdown):
        # root = os.path.dirname(os.path.dirname(self.page.url))
        root = self.page.url

        paths = []

        p = re.compile("!\[.*\]\((.*)\)")
        it = p.finditer(markdown)
        for match in it:
            path = match.group(1)
            paths.append(path)

            destinationPath = os.path.realpath(self.config['base_path'] + "/" +
                                               root + "/gen_/" + path)

            if not os.path.isfile(destinationPath):
                print("Copying image: " + path + " to " + destinationPath)

                os.makedirs(os.path.dirname(destinationPath), exist_ok=True)
                shutil.copyfile(tmpRoot + "/" + path, destinationPath)

        for path in paths:
            markdown = markdown.replace(path, "gen_/" + path)

        return markdown 
    """

    @env.macro
    def snippet(file_path, section_name, num_sections=1):
        p = re.compile("^#+ ")
        m = p.search(section_name)
        if m:
            section_level = m.span()[1] - 1
            root = env.variables['config']['docs_dir']
            full_path = os.path.join(root, file_path)

            content = ""
            with open(full_path, 'r') as myfile:
                content = myfile.read()

            p = re.compile("^" + section_name + "$", re.MULTILINE)
            start = p.search(content)
            start_span = start.span()
            p = re.compile("^#{1," + str(section_level) + "} ", re.MULTILINE)

            result = ""            
            all = [x for x in p.finditer(content[start_span[1]:])]
            
            print (len(all))

            if len(all) == 0 or (num_sections-1) >= len(all):
                result = content[start_span[0]:]
            else:
                end = all[num_sections-1]
                end_index = end.span()[0]
                result = content[start_span[0]:end_index + start_span[1]]

            # If there are any images, find them, copy them
            # result = copy_markdown_images(root, result)
            return result
        else:
            return "Heading reference beginning in # is required"
